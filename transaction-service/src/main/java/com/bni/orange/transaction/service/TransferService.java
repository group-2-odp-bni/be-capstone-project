package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.UserServiceClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.config.properties.TransferLimitProperties;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.enums.TransferType;
import com.bni.orange.transaction.model.request.InternalTransferRequest;
import com.bni.orange.transaction.model.request.RecipientLookupRequest;
import com.bni.orange.transaction.model.request.TransferConfirmRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.response.RecipientLookupResponse;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import com.bni.orange.transaction.model.response.WalletResolutionResponse;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.service.helper.TransactionFactory;
import com.bni.orange.transaction.service.helper.TransactionMapper;
import com.bni.orange.transaction.service.helper.TransferFinalizer;
import com.bni.orange.transaction.service.helper.TransferOrchestrator;
import com.bni.orange.transaction.service.helper.TransferValidator;
import com.bni.orange.transaction.util.SecurityContextPropagationExecutor;
import com.bni.orange.transaction.utils.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final TransactionMapper transactionMapper;
    private final TransferValidator transferValidator;
    private final TransactionFactory transactionFactory;
    private final TransferOrchestrator transferOrchestrator;
    private final TransferFinalizer transferFinalizer;
    private final TransferLimitProperties transferLimitProperties;
    private final SecurityContextPropagationExecutor securityContextPropagationExecutor;

    public RecipientLookupResponse inquiry(RecipientLookupRequest request, UUID currentUserId, String accessToken) {
        log.info("Looking up recipient for phone: {}", request.phoneNumber());

        var normalizedPhone = PhoneNumberUtils.normalize(request.phoneNumber());

        if (!PhoneNumberUtils.isValid(normalizedPhone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_NUMBER, "Invalid phone number format: " + request.phoneNumber());
        }

        var userFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() -> Optional
            .ofNullable(userServiceClient.findByPhoneNumber(normalizedPhone, accessToken).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"))
        );

        try {
            var user = userFuture.join();

            if (user.id().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Cannot transfer money to yourself");
            }

            var walletFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() -> {
                try {
                    var wallet = walletServiceClient
                        .getDefaultWalletByUserId(user.id())
                        .block();

                    if (wallet != null && wallet.walletId() == null) {
                        log.warn("User {} has no default wallet configured", user.id());
                        return null;
                    }
                    return wallet;
                } catch (BusinessException ex) {
                    if (ex.getErrorCode().equals(ErrorCode.WALLET_NOT_FOUND)) {
                        log.warn("User {} has no default wallet configured", user.id());
                        return null;
                    }
                    throw ex;
                }
            });

            var resolvedWallet = walletFuture.join();
            return buildLookupResponse(user, resolvedWallet, normalizedPhone);

        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during recipient inquiry", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Transactional
    public TransactionResponse initiateTransfer(
        TransferInitiateRequest request,
        UUID senderUserId,
        String idempotencyKey,
        String accessToken
    ) {
        log.info("Initiating transfer from user {} to user {}, amount: {}, senderWallet: {}",
            senderUserId, request.receiverUserId(), request.amount(), request.senderWalletId());

        var existingTransaction = transferValidator.validateInitiateTransferRequest(request, senderUserId, idempotencyKey);
        if (existingTransaction.isPresent()) {
            return transactionMapper.toResponse(existingTransaction.get());
        }

        var totalAmount = request.amount().add(transactionFactory.getTransferFee());

        var walletAccessValidationFuture = securityContextPropagationExecutor.runAsyncWithContext(() ->
            transferValidator.validateSenderWalletAccess(senderUserId, request.senderWalletId(), totalAmount, TransferType.EXTERNAL)
        );

        var balanceValidationFuture = securityContextPropagationExecutor.runAsyncWithContext(() ->
            transferValidator.validateBalance(request.senderWalletId(), totalAmount, senderUserId)
        );

        var receiverInfoFuture = transferValidator.validateAndGetReceiverInfoAsync(request.receiverUserId(), accessToken);

        var senderInfoFuture = transferValidator.validateAndGetSenderInfoAsync(senderUserId, accessToken);

        try {
            CompletableFuture.allOf(walletAccessValidationFuture, balanceValidationFuture, receiverInfoFuture, senderInfoFuture).join();

            var receiverInfo = receiverInfoFuture.join();
            var senderInfo = senderInfoFuture.join();
            var transaction = transactionFactory.createPendingTransfer(request, senderUserId, idempotencyKey, senderInfo, receiverInfo);
            return transactionMapper.toResponse(transaction);
        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during parallel transfer validation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
    @Transactional
    public TransactionResponse confirmTransfer(
        UUID transactionId,
        TransferConfirmRequest request,
        UUID currentUserId,
        String accessToken
    ) {
        log.info("Confirming transfer: {}", transactionId);

        var transactionFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() ->
            transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found: " + transactionId))
        );

        var pinVerificationFuture = securityContextPropagationExecutor.runAsyncWithContext(() ->
            transferValidator.validatePin(request.pin(), accessToken)
        );

        try {
            CompletableFuture.allOf(transactionFuture, pinVerificationFuture).join();

            var transaction = transactionFuture.join();

            if (!transaction.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found or you don't have permission");
            }

            if (!transaction.getStatus().canBeProcessed()) {
                throw new BusinessException(ErrorCode.INVALID_TRANSACTION_STATE, "Transaction cannot be processed in current state: " + transaction.getStatus());
            }

            transaction.markAsProcessing();
            transactionRepository.save(transaction);

            return transferOrchestrator.executeTransfer(transaction);

        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during transfer confirmation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private RecipientLookupResponse buildLookupResponse(
        UserProfileResponse user,
        WalletResolutionResponse resolvedWallet,
        String normalizedPhone
    ) {
        var builder = RecipientLookupResponse.builder()
            .userId(user.id())
            .name(user.name())
            .phoneNumber(normalizedPhone)
            .formattedPhone(PhoneNumberUtils.format(normalizedPhone))
            .profileImageUrl(user.profileImageUrl())
            .hasWallet(resolvedWallet != null);

        Optional.ofNullable(resolvedWallet).ifPresent(w -> builder
            .walletId(w.walletId())
            .walletCurrency(w.currency())
            .walletName(w.walletName())
            .walletType(w.walletType()));

        return builder.build();
    }

    @Transactional
    public TransactionResponse initiateInternalTransfer(
        InternalTransferRequest request,
        UUID userId,
        String idempotencyKey
    ) {
        log.info("Initiating internal transfer from user {}, from wallet {} to wallet {}, amount: {}",
            userId, request.sourceWalletId(), request.destinationWalletId(), request.amount());

        var existingTransaction = transferValidator.validateInitiateInternalTransferRequest(request, idempotencyKey);
        if (existingTransaction.isPresent()) {
            return transactionMapper.toResponse(existingTransaction.get());
        }

        var ownershipValidationFuture = securityContextPropagationExecutor.supplyAsyncWithContext(() ->
            transferValidator.validateWalletOwnership(userId, List.of(request.sourceWalletId(), request.destinationWalletId()))
        );

        var totalAmount = request.amount();

        var walletAccessValidationFuture = securityContextPropagationExecutor.runAsyncWithContext(() ->
            transferValidator.validateSenderWalletAccess(userId, request.sourceWalletId(), totalAmount, TransferType.INTERNAL)
        );

        var balanceValidationFuture = securityContextPropagationExecutor.runAsyncWithContext(() ->
            transferValidator.validateBalance(request.sourceWalletId(), totalAmount, userId)
        );

        try {
            CompletableFuture.allOf(ownershipValidationFuture, walletAccessValidationFuture, balanceValidationFuture).join();

            var ownershipValidation = ownershipValidationFuture.join();
            var sourceWalletName = ownershipValidation.walletNames().getOrDefault(request.sourceWalletId(), "Unknown Wallet");
            var destinationWalletName = ownershipValidation.walletNames().getOrDefault(request.destinationWalletId(), "Unknown Wallet");

            var pendingTransaction = transactionFactory.createAndSavePendingInternalTransaction(
                request,
                userId,
                idempotencyKey,
                sourceWalletName,
                destinationWalletName
            );

            pendingTransaction.markAsProcessing();
            var processingTransaction = transactionRepository.save(pendingTransaction);

            return transferOrchestrator.executeInternalTransferSaga(processingTransaction);
        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during parallel internal transfer validation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
