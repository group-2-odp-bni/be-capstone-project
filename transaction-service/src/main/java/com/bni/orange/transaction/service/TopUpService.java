package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.BniVaClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.event.TopUpEventPublisher;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
import com.bni.orange.transaction.model.enums.WalletPermission;
import com.bni.orange.transaction.model.enums.WalletRole;
import com.bni.orange.transaction.model.enums.WalletStatus;
import com.bni.orange.transaction.model.request.BalanceAdjustmentRequest;
import com.bni.orange.transaction.model.request.TopUpCallbackRequest;
import com.bni.orange.transaction.model.request.TopUpInitiateRequest;
import com.bni.orange.transaction.model.response.PaymentMethodResponse;
import com.bni.orange.transaction.model.response.TopUpInitiateResponse;
import com.bni.orange.transaction.model.response.VirtualAccountResponse;
import com.bni.orange.transaction.model.response.WalletAccessValidation;
import com.bni.orange.transaction.model.response.WalletInfo;
import com.bni.orange.transaction.repository.TopUpConfigRepository;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.repository.VirtualAccountRepository;
import com.bni.orange.transaction.utils.TransactionRefGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpService {

    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final TopUpConfigRepository topUpConfigRepository;
    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountService virtualAccountService;
    private final WalletServiceClient walletServiceClient;
    private final BniVaClient bniVaClient;
    private final TransactionRefGenerator refGenerator;
    private final TopUpEventPublisher eventPublisher;
    private final WebhookSignatureValidator signatureValidator;

    public List<PaymentMethodResponse> getPaymentMethods() {
        log.info("Fetching active payment methods");

        return topUpConfigRepository.findAllActiveProviders()
            .stream()
            .map(config -> new PaymentMethodResponse(
                config.getProvider(),
                config.getProviderName(),
                config.getMinAmount(),
                config.getMaxAmount(),
                config.getFeeAmount(),
                config.getFeePercentage(),
                config.getIconUrl(),
                config.getDisplayOrder()
            ))
            .toList();
    }

    @Transactional
    public TopUpInitiateResponse initiateTopUp(TopUpInitiateRequest request, UUID userId) {
        log.info("Initiating top-up for user {} with provider {} and amount {}", userId, request.provider(), request.amount());

        var validation = validateWalletAccess(userId, request.walletId());

        var config = topUpConfigRepository
            .findActiveByProvider(request.provider())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_PROVIDER_NOT_AVAILABLE, "Payment provider not available: " + request.provider()));

        if (!config.isAmountValid(request.amount())) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, String.format("Amount must be between %s and %s", config.getMinAmount(), config.getMaxAmount()));
        }

        var fee = config.calculateFee(request.amount());
        var totalAmount = request.amount().add(fee);

        var transactionRef = refGenerator.generate();

        var transaction = transactionRepository.save(
            Transaction.builder()
                .transactionRef(transactionRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .type(TransactionType.TOP_UP)
                .status(TransactionStatus.PENDING)
                .amount(request.amount())
                .fee(fee)
                .totalAmount(totalAmount)
                .currency("IDR")
                .senderUserId(userId)
                .senderWalletId(request.walletId())
                .receiverUserId(userId)
                .receiverWalletId(request.walletId())
                .description("Top-up via " + config.getProviderName())
                .build()
        );
        log.info("Created transaction: {}", transactionRef);

        var vaNumber = virtualAccountService.generateVaNumber(config, userId);

        var virtualAccount = virtualAccountRepository.save(VirtualAccount.builder()
            .vaNumber(vaNumber)
            .transactionId(transaction.getId())
            .userId(userId)
            .walletId(request.walletId())
            .provider(request.provider())
            .status(VirtualAccountStatus.ACTIVE)
            .amount(request.amount())
            .expiresAt(virtualAccountService.calculateExpiryTime(config))
            .metadata(Map.of(
                "provider", request.provider().name(),
                "transactionRef", transactionRef
            ))
            .build());
        log.info("Created virtual account: {}", vaNumber);

        eventPublisher.publishTopUpInitiated(transaction, virtualAccount);

        try {
            var vaRequest = new BniVaClient.VaRegistrationRequest(
                vaNumber,
                request.amount(),
                "User-" + userId,
                virtualAccount.getExpiresAt().format(EXPIRY_FORMATTER)
            );

            var vaResponse = bniVaClient.registerVirtualAccount(vaRequest);

            if (!vaResponse.success()) {
                throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, "Failed to register VA with provider: " + vaResponse.message());
            }

            log.info("VA registered with provider successfully");
        } catch (Exception e) {
            log.error("Failed to register VA with provider", e);
            transaction.markAsFailed("Failed to register VA with provider: " + e.getMessage());
            transactionRepository.save(transaction);
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR, "Failed to register VA with payment provider");
        }

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
            .vaNumber(vaNumber)
            .provider(request.provider())
            .status(virtualAccount.getStatus())
            .amount(request.amount())
            .expiresAt(virtualAccount.getExpiresAt())
            .createdAt(transaction.getCreatedAt())
            .wallet(walletInfo)
            .build();
    }

    @Transactional
    public void processPaymentCallback(PaymentProvider provider, TopUpCallbackRequest request, String signature) {
        log.info("Processing payment callback for VA: {} from provider: {}",
            request.vaNumber(), provider);

        signatureValidator.validateWebhookRequest(provider, request, signature);

        var virtualAccount = virtualAccountService.findByVaNumberWithLock(request.vaNumber());

        if (!virtualAccount.getProvider().equals(provider)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Provider mismatch: expected " + virtualAccount.getProvider() + ", got " + provider);
        }

        if (virtualAccount.getStatus() != VirtualAccountStatus.ACTIVE) {
            log.warn("VA already processed: {}, status: {}", request.vaNumber(), virtualAccount.getStatus());
            return;
        }

        if (virtualAccount.isExpired()) {
            log.warn("VA expired: {}", request.vaNumber());
            throw new BusinessException(ErrorCode.VIRTUAL_ACCOUNT_EXPIRED, "Virtual Account has expired");
        }

        var transaction = transactionRepository
            .findById(virtualAccount.getTransactionId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));

        if (request.paidAmount().compareTo(virtualAccount.getAmount()) < 0) {
            log.error("Paid amount {} is less than expected amount {}", request.paidAmount(), virtualAccount.getAmount());
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Paid amount does not match expected amount");
        }

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

        try {
            var adjustmentRequest = BalanceAdjustmentRequest.builder()
                .amount(virtualAccount.getAmount())
                .reason(transaction.getTransactionRef())
                .description("Top-up via " + provider.getDisplayName())
                .build();

            walletServiceClient.adjustBalance(virtualAccount.getWalletId(), adjustmentRequest, "system-topup-service").block();

            transaction.markAsSuccess();
            transactionRepository.save(transaction);

            log.info("Top-up completed successfully for transaction: {}", transaction.getTransactionRef());

            eventPublisher.publishTopUpCompleted(transaction, virtualAccount);

        } catch (Exception e) {
            log.error("Failed to credit wallet balance", e);
            transaction.markAsFailed("Failed to credit wallet: " + e.getMessage());
            transactionRepository.save(transaction);

            eventPublisher.publishTopUpFailed(transaction, virtualAccount, e.getMessage());

            throw new BusinessException(ErrorCode.WALLET_UPDATE_FAILED, "Failed to credit wallet balance");
        }
    }

    public VirtualAccountResponse getTopUpStatus(UUID transactionId, UUID userId) {
        log.info("Getting top-up status for transaction: {}", transactionId);

        var virtualAccount = virtualAccountService.findByTransactionId(transactionId);

        if (!virtualAccount.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User does not own this virtual account");
        }

        var transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));

        return VirtualAccountResponse.builder()
            .id(virtualAccount.getId())
            .vaNumber(virtualAccount.getVaNumber())
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

    @Transactional
    public void cancelTopUp(UUID transactionId, UUID userId) {
        log.info("Cancelling top-up for transaction: {}", transactionId);

        var virtualAccount = virtualAccountService.findByTransactionId(transactionId);

        if (!virtualAccount.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User does not own this virtual account");
        }

        if (!virtualAccount.getStatus().canBeCancelled()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "Virtual Account cannot be cancelled in current state: " + virtualAccount.getStatus());
        }

        var transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found"));

        try {
            bniVaClient.cancelVirtualAccount(virtualAccount.getVaNumber());
        } catch (Exception e) {
            log.error("Failed to cancel VA with provider", e);
        }

        virtualAccount.markAsCancelled();
        virtualAccountRepository.save(virtualAccount);

        transaction.markAsFailed("Cancelled by user");
        transactionRepository.save(transaction);

        eventPublisher.publishTopUpCancelled(transaction, virtualAccount);

        log.info("Top-up cancelled successfully for transaction: {}", transactionId);
    }

    private WalletAccessValidation validateWalletAccess(UUID userId, UUID walletId) {
        log.debug("Validating wallet access for user {} on wallet {}", userId, walletId);

        var validation = walletServiceClient.validateAccess(walletId, userId, WalletPermission.TRANSACT).block();

        if (validation == null) {
            log.error("Wallet validation returned null for walletId={}, userId={}", walletId, userId);
            throw new BusinessException(
                ErrorCode.WALLET_SERVICE_ERROR,
                "Failed to validate wallet access"
            );
        }

        if (!validation.hasAccess()) {
            log.warn("Wallet access denied: userId={}, walletId={}, reason={}",
                userId, walletId, validation.denialReason());
            throw new BusinessException(
                ErrorCode.WALLET_ACCESS_DENIED,
                validation.denialReason() != null ? validation.denialReason() : "User does not have access to this wallet"
            );
        }

        if (validation.walletStatus() != WalletStatus.ACTIVE) {
            log.warn("Wallet not active: userId={}, walletId={}, status={}",
                userId, walletId, validation.walletStatus());
            throw new BusinessException(
                ErrorCode.WALLET_NOT_ACTIVE,
                "Wallet is not active: " + validation.walletStatus()
            );
        }

        if (validation.userRole() == WalletRole.VIEWER) {
            log.warn("Insufficient permissions: userId={}, walletId={}, role={}", userId, walletId, validation.userRole());
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_PERMISSIONS,
                "Role VIEWER cannot initiate top-up transactions"
            );
        }

        log.info("Wallet access validated successfully: userId={}, walletId={}, role={}, walletType={}",
            userId, walletId, validation.userRole(), validation.walletType());

        return validation;
    }
}
