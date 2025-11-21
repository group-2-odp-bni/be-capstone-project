package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.UserServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.request.TopUpCallbackRequest;
import com.bni.orange.transaction.model.request.TopUpInitiateRequest;
import com.bni.orange.transaction.model.response.PaymentMethodResponse;
import com.bni.orange.transaction.model.response.TopUpInitiateResponse;
import com.bni.orange.transaction.model.response.VirtualAccountResponse;
import com.bni.orange.transaction.model.response.WalletInfo;
import com.bni.orange.transaction.repository.TopUpConfigRepository;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.repository.VirtualAccountRepository;
import com.bni.orange.transaction.service.helper.TopUpFactory;
import com.bni.orange.transaction.service.helper.TopUpFinalizer;
import com.bni.orange.transaction.service.helper.TopUpOrchestrator;
import com.bni.orange.transaction.service.helper.TopUpValidator;
import com.bni.orange.transaction.utils.SecurityContextPropagationExecutor;
import com.bni.orange.transaction.utils.TransactionRefGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpService {

    private final TopUpConfigRepository topUpConfigRepository;
    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountService virtualAccountService;
    private final UserServiceClient userServiceClient;
    private final TransactionRefGenerator refGenerator;
    private final SecurityContextPropagationExecutor securityContextPropagationExecutor;
    private final TopUpValidator topUpValidator;
    private final TopUpFactory topUpFactory;
    private final TopUpOrchestrator topUpOrchestrator;
    private final TopUpFinalizer topUpFinalizer;

    public List<PaymentMethodResponse> getPaymentMethods() {
        log.info("Fetching active payment methods");

        return topUpConfigRepository.findAllActiveProviders()
            .stream()
            .map(config -> PaymentMethodResponse.builder()
                .provider(config.getProvider())
                .providerName(config.getProviderName())
                .minAmount(config.getMinAmount())
                .maxAmount(config.getMaxAmount())
                .feeAmount(config.getFeeAmount())
                .feePercentage(config.getFeePercentage())
                .iconUrl(config.getIconUrl())
                .displayOrder(config.getDisplayOrder())
                .build()
            )
            .toList();
    }

    @Transactional
    public TopUpInitiateResponse initiateTopUp(TopUpInitiateRequest request, UUID userId, String accessToken) {
        log.info("Initiating top-up for user {} with provider {} and amount {}", userId, request.provider(), request.amount());

        var validationFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() ->
            topUpValidator.validateWalletAccess(userId, request.walletId())
        );

        var configFuture = this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> topUpConfigRepository
                .findActiveByProvider(request.provider())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_PROVIDER_NOT_AVAILABLE, "Payment provider not available: " + request.provider()))
        );

        var userProfileFuture = this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> userServiceClient
                .findById(userId, accessToken)
                .block()
        );

        try {
            CompletableFuture.allOf(validationFuture, configFuture, userProfileFuture).join();

            var validation = validationFuture.join();
            var config = configFuture.join();
            var userProfile = userProfileFuture.join();

            topUpValidator.validateTopUpAmount(config, request.amount());

            var fee = config.calculateFee(request.amount());
            var totalAmount = request.amount().add(fee);

            var transactionRef = refGenerator.generate();

            var transaction = topUpFactory
                .createPendingTopUpTransaction(request, userId, config, transactionRef, fee, totalAmount, userProfile);

            var virtualAccount = topUpFactory.createVirtualAccount(request, transaction, config, userProfile);

            topUpOrchestrator.registerVirtualAccount(virtualAccount, transaction);

            var walletInfo = WalletInfo.builder()
                .id(request.walletId())
                .name(validation.walletName())
                .type(validation.walletType())
                .userRole(validation.userRole())
                .build();

            return TopUpInitiateResponse.builder()
                .transactionId(transaction.getId())
                .transactionRef(transactionRef)
                .virtualAccountId(virtualAccount.getId())
                .vaNumber(virtualAccount.getVaNumber())
                .provider(request.provider())
                .status(virtualAccount.getStatus())
                .amount(request.amount())
                .expiresAt(virtualAccount.getExpiresAt())
                .createdAt(transaction.getCreatedAt())
                .wallet(walletInfo)
                .build();

        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during top-up initiation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void processPaymentCallback(PaymentProvider provider, TopUpCallbackRequest request, String signature) {
        log.info("Processing payment callback for VA: {} from provider: {}", request.vaNumber(), provider);

        var virtualAccount = virtualAccountService.findByVaNumberWithLock(request.vaNumber());

        var shouldProcess = topUpValidator.validateCallback(provider, request, signature, virtualAccount);
        if (!shouldProcess) {
            log.info("Skipping callback processing - VA already processed: {}", request.vaNumber());
            return;
        }

        var transaction = transactionRepository
            .findById(virtualAccount.getTransactionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));

        transaction.markAsProcessing();
        transactionRepository.save(transaction);

        var callbackPayload = new HashMap<String, Object>();
        callbackPayload.put("paymentReference", request.paymentReference());
        callbackPayload.put("paymentTimestamp", request.paymentTimestamp());
        callbackPayload.put("paidAmount", request.paidAmount());
        if (request.additionalData() != null) {
            callbackPayload.putAll(request.additionalData());
        }

        virtualAccount.markAsPaid(request.paidAmount(), callbackPayload);
        virtualAccountRepository.save(virtualAccount);

        topUpOrchestrator.processWalletCredit(transaction, virtualAccount, provider);
    }

    public VirtualAccountResponse getTopUpStatus(UUID transactionId, UUID userId) {
        log.info("Getting top-up status for transaction: {}", transactionId);

        var virtualAccountFuture = this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> virtualAccountService.findByTransactionId(transactionId)
        );

        var transactionFuture = this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"))
        );

        try {
            CompletableFuture.allOf(virtualAccountFuture, transactionFuture).join();

            var virtualAccount = virtualAccountFuture.join();
            var transaction = transactionFuture.join();

            if (!virtualAccount.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "User does not own this virtual account");
            }

            return VirtualAccountResponse.builder()
                .id(virtualAccount.getId())
                .vaNumber(virtualAccount.getVaNumber())
                .accountName(virtualAccount.getAccountName())
                .transactionId(transaction.getId())
                .transactionRef(transaction.getTransactionRef())
                .provider(virtualAccount.getProvider())
                .status(virtualAccount.getStatus())
                .amount(virtualAccount.getAmount())
                .paidAmount(virtualAccount.getPaidAmount())
                .expiresAt(virtualAccount.getExpiresAt())
                .paidAt(virtualAccount.getPaidAt())
                .createdAt(virtualAccount.getCreatedAt())
                .build();

        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during top-up status retrieval", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Transactional
    public void cancelTopUp(UUID transactionId, UUID userId) {
        log.info("Cancelling top-up for transaction: {}", transactionId);

        var virtualAccountFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() ->
            virtualAccountService.findByTransactionId(transactionId)
        );

        var transactionFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() ->
                transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"))
        );

        try {
            CompletableFuture.allOf(virtualAccountFuture, transactionFuture).join();

            var virtualAccount = virtualAccountFuture.join();
            var transaction = transactionFuture.join();

            if (!virtualAccount.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "User does not own this virtual account");
            }

            topUpValidator.validateCancellation(virtualAccount);

            topUpOrchestrator.cancelVirtualAccount(virtualAccount);

            virtualAccount.markAsCancelled();
            virtualAccountRepository.save(virtualAccount);

            transaction.markAsFailed("Cancelled by user");
            transactionRepository.save(transaction);

            topUpFinalizer.finalizeFailure(transaction, virtualAccount, "Cancelled by user");

            log.info("Top-up cancelled successfully for transaction: {}", transactionId);

        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during top-up cancellation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    public VirtualAccountResponse inquiryByVaNumber(String vaNumber) {
        log.info("Inquiry for VA number: {}", vaNumber);

        var virtualAccount = virtualAccountRepository
            .findByVaNumber(vaNumber)
            .orElseThrow(() -> new BusinessException(ErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND, "Virtual Account not found"));

        var transaction = transactionRepository
            .findById(virtualAccount.getTransactionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));

        log.info("VA inquiry successful: vaNumber={}, status={}, amount={}",
            vaNumber, virtualAccount.getStatus(), virtualAccount.getAmount());

        return VirtualAccountResponse.builder()
            .id(virtualAccount.getId())
            .vaNumber(virtualAccount.getVaNumber())
            .accountName(virtualAccount.getAccountName())
            .transactionId(transaction.getId())
            .transactionRef(transaction.getTransactionRef())
            .provider(virtualAccount.getProvider())
            .status(virtualAccount.getStatus())
            .amount(virtualAccount.getAmount())
            .paidAmount(virtualAccount.getPaidAmount())
            .expiresAt(virtualAccount.getExpiresAt())
            .paidAt(virtualAccount.getPaidAt())
            .createdAt(virtualAccount.getCreatedAt())
            .build();
    }
}
