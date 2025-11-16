package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.client.AuthServiceClient;
import com.bni.orange.transaction.client.UserServiceClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.config.properties.TransferLimitProperties;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.enums.InternalAction;
import com.bni.orange.transaction.model.enums.TransferType;
import com.bni.orange.transaction.model.request.InternalTransferRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.request.internal.BalanceValidateRequest;
import com.bni.orange.transaction.model.request.internal.RoleValidateRequest;
import com.bni.orange.transaction.model.request.internal.ValidateWalletOwnershipRequest;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import com.bni.orange.transaction.model.response.internal.ValidateWalletOwnershipResponse;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.util.SecurityContextPropagationExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferValidator {

    private final TransactionRepository transactionRepository;
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final AuthServiceClient authServiceClient;
    private final TransferLimitProperties transferLimitProperties;
    private final SecurityContextPropagationExecutor securityContextPropagationExecutor;

    public Optional<Transaction> validateInitiateTransferRequest(TransferInitiateRequest request, UUID senderUserId, String idempotencyKey) {
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            var existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
            log.warn("Duplicate transaction detected, returning existing: {}", existing.getTransactionRef());
            return Optional.of(existing);
        }

        if (request.amount().compareTo(transferLimitProperties.minAmount()) < 0 ||
            request.amount().compareTo(transferLimitProperties.maxAmount()) > 0) {
            throw new BusinessException(
                ErrorCode.INVALID_AMOUNT,
                "Transfer amount must be between %s and %s".formatted(transferLimitProperties.minAmount(), transferLimitProperties.maxAmount())
            );
        }

        if (request.receiverUserId().equals(senderUserId)) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Cannot transfer money to yourself");
        }

        return Optional.empty();
    }

    public Optional<Transaction> validateInitiateInternalTransferRequest(InternalTransferRequest request, String idempotencyKey) {
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            var existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
            log.warn("Duplicate internal transfer detected, returning existing: {}", existing.getTransactionRef());
            return Optional.of(existing);
        }

        if (request.sourceWalletId().equals(request.destinationWalletId())) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Source and destination wallets cannot be the same");
        }

        return Optional.empty();
    }

    public void validateSenderWalletAccess(UUID userId, UUID walletId, BigDecimal amount, TransferType transferType) {
        var roleValidationRequest = RoleValidateRequest.builder()
            .walletId(walletId)
            .userId(userId)
            .action(InternalAction.DEBIT)
            .amount(amount)
            .transferType(transferType)
            .build();

        var roleValidation = Optional
            .ofNullable(walletServiceClient.validateRole(roleValidationRequest).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR,
                "Failed to validate wallet access"));

        if (!roleValidation.allowed()) {
            log.warn("Wallet access denied: userId={}, walletId={}, code={}, message={}",
                userId, walletId, roleValidation.code(), roleValidation.message());

            if ("LIMIT_EXCEEDED".equals(roleValidation.code())) {
                throw new BusinessException(ErrorCode.SPENDING_LIMIT_EXCEEDED, roleValidation.message());
            } else if ("WALLET_NOT_ACTIVE".equals(roleValidation.code())) {
                throw new BusinessException(ErrorCode.WALLET_NOT_ACTIVE, roleValidation.message());
            } else {
                throw new BusinessException(ErrorCode.WALLET_ACCESS_DENIED, roleValidation.message());
            }
        }

        log.debug("Wallet access validated successfully: userId={}, walletId={}, role={}",
            userId, walletId, roleValidation.effectiveRole());
    }

    public CompletableFuture<UserProfileResponse> validateAndGetReceiverInfoAsync(UUID receiverUserId, String accessToken) {
        return this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> Optional
                .ofNullable(userServiceClient.findById(receiverUserId, accessToken).block())
                .orElse(UserProfileResponse.builder()
                    .id(receiverUserId)
                    .name("Unknown")
                    .phoneNumber("")
                    .build())
            );
    }

    public CompletableFuture<UserProfileResponse> validateAndGetSenderInfoAsync(UUID senderUserId, String accessToken) {
        return this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> Optional
                .ofNullable(userServiceClient.findById(senderUserId, accessToken).block())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Sender user not found"))
            );
    }

    public void validateBalance(UUID walletId, BigDecimal totalAmount, UUID actorUserId) {
        var balanceValidationRequest = BalanceValidateRequest.builder()
            .walletId(walletId)
            .amount(totalAmount)
            .action(InternalAction.DEBIT)
            .actorUserId(actorUserId)
            .build();

        var balanceValidation = this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> Optional
                .ofNullable(walletServiceClient.validateBalance(balanceValidationRequest).block())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to validate balance"))
            )
            .join();

        if (!balanceValidation.allowed()) {
            log.warn("Balance validation failed: walletId={}, code={}, message={}",
                walletId, balanceValidation.code(), balanceValidation.message());

            if ("INSUFFICIENT_BALANCE".equals(balanceValidation.code())) {
                var availableBalance = balanceValidation.extras().get("balance");
                throw new BusinessException(
                    ErrorCode.INSUFFICIENT_BALANCE,
                    String.format("Insufficient balance. Required: %s, Available: %s", totalAmount, availableBalance)
                );
            } else {
                throw new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, balanceValidation.message());
            }
        }
    }

    public ValidateWalletOwnershipResponse validateWalletOwnership(UUID userId, List<UUID> walletIds) {
        var req = ValidateWalletOwnershipRequest.of(userId, walletIds);
        var res = this
            .securityContextPropagationExecutor
            .supplyAsyncWithContext(() -> walletServiceClient.validateWalletOwnership(req).block())
            .join();

        if (res == null || !res.isOwner()) {
            throw new BusinessException(ErrorCode.WALLET_ACCESS_DENIED, "User does not own one or both wallets");
        }
        return res;
    }

    public void validatePin(String pin, String accessToken) {
        if (!Boolean.TRUE.equals(authServiceClient.verifyPin(pin, accessToken).block())) {
            throw new BusinessException(ErrorCode.INVALID_PIN, "Invalid PIN provided");
        }
    }
}
