package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.entity.TopUpConfig;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.model.enums.InternalAction;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
import com.bni.orange.transaction.model.enums.WalletRole;
import com.bni.orange.transaction.model.enums.WalletStatus;
import com.bni.orange.transaction.model.request.TopUpCallbackRequest;
import com.bni.orange.transaction.model.request.internal.RoleValidateRequest;
import com.bni.orange.transaction.model.response.WalletAccessValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpValidator {

    private final WalletServiceClient walletServiceClient;
    private final WebhookSignatureValidator signatureValidator;

    public WalletAccessValidation validateWalletAccess(UUID userId, UUID walletId) {
        log.debug("Validating wallet access for user {} on wallet {}", userId, walletId);

        var roleValidationRequest = RoleValidateRequest.builder()
            .walletId(walletId)
            .userId(userId)
            .action(InternalAction.CREDIT)
            .build();

        var roleValidation = walletServiceClient.validateRole(roleValidationRequest).block();

        if (roleValidation == null) {
            log.error("Role validation returned null for walletId={}, userId={}", walletId, userId);
            throw new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to validate wallet access");
        }

        if (!roleValidation.allowed()) {
            log.warn("Wallet access denied: userId={}, walletId={}, code={}, message={}",
                userId, walletId, roleValidation.code(), roleValidation.message());
            throw new BusinessException(ErrorCode.WALLET_ACCESS_DENIED, roleValidation.message());
        }

        var currency = (String) roleValidation.extras().get("currency");

        log.info("Wallet access validated successfully: userId={}, walletId={}, role={}, currency={}",
            userId, walletId, roleValidation.effectiveRole(), currency);

        return WalletAccessValidation.builder()
            .hasAccess(true)
            .walletStatus(WalletStatus.ACTIVE)
            .userRole(WalletRole.valueOf(roleValidation.effectiveRole()))
            .walletType(null)
            .walletName(null)
            .build();
    }

    public void validateTopUpAmount(TopUpConfig config, BigDecimal amount) {
        if (!config.isAmountValid(amount)) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, String.format("Amount must be between %s and %s", config.getMinAmount(), config.getMaxAmount()));
        }
    }

    public boolean validateCallback(PaymentProvider provider, TopUpCallbackRequest request, String signature, VirtualAccount virtualAccount) {
        signatureValidator.validateWebhookRequest(provider, request, signature);

        if (!virtualAccount.getProvider().equals(provider)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Provider mismatch: expected " + virtualAccount.getProvider() + ", got " + provider);
        }

        if (virtualAccount.getStatus() != VirtualAccountStatus.ACTIVE) {
            log.warn("VA already processed: {}, status: {}", request.vaNumber(), virtualAccount.getStatus());
            return false;
        }

        if (virtualAccount.isExpired()) {
            log.warn("VA expired: {}", request.vaNumber());
            throw new BusinessException(ErrorCode.VIRTUAL_ACCOUNT_EXPIRED, "Virtual Account has expired");
        }

        if (request.paidAmount().compareTo(virtualAccount.getAmount()) < 0) {
            log.error("Paid amount {} is less than expected amount {}", request.paidAmount(), virtualAccount.getAmount());
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Paid amount does not match expected amount");
        }

        return true;
    }

    public void validateCancellation(VirtualAccount virtualAccount) {
        if (!virtualAccount.getStatus().canBeCancelled()) {
            throw new BusinessException(ErrorCode.INVALID_STATUS, "Virtual Account cannot be cancelled in current state: " + virtualAccount.getStatus());
        }
    }
}
