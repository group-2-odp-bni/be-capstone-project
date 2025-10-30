package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.BniVaClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.event.TopUpEventPublisher;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.TransactionLedger;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
import com.bni.orange.transaction.model.enums.WalletRole;
import com.bni.orange.transaction.model.enums.WalletStatus;
import com.bni.orange.transaction.model.request.TopUpCallbackRequest;
import com.bni.orange.transaction.model.request.TopUpInitiateRequest;
import com.bni.orange.transaction.model.response.PaymentMethodResponse;
import com.bni.orange.transaction.model.response.TopUpInitiateResponse;
import com.bni.orange.transaction.model.response.VirtualAccountResponse;
import com.bni.orange.transaction.model.response.WalletAccessValidation;
import com.bni.orange.transaction.model.response.WalletInfo;
import com.bni.orange.transaction.repository.TopUpConfigRepository;
import com.bni.orange.transaction.repository.TransactionLedgerRepository;
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
    private final TransactionLedgerRepository ledgerRepository;
    private final WebhookSignatureValidator signatureValidator;

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

        var virtualAccount = virtualAccountRepository.save(
            VirtualAccount.builder()
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

        // eventPublisher.publishTopUpInitiated(transaction, virtualAccount);

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
            var balanceUpdateRequest = com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest.builder()
                .walletId(virtualAccount.getWalletId())
                .delta(virtualAccount.getAmount())
                .referenceId(transaction.getTransactionRef())
                .reason("Top-up via " + provider.getDisplayName())
                .build();

            var balanceUpdateResult = walletServiceClient.updateBalance(balanceUpdateRequest).block();

            if (balanceUpdateResult == null || !"OK".equals(balanceUpdateResult.code())) {
                throw new BusinessException(
                    ErrorCode.WALLET_UPDATE_FAILED,
                    balanceUpdateResult != null ? balanceUpdateResult.message() : "Failed to update wallet balance"
                );
            }

            transaction.markAsSuccess();
            transactionRepository.save(transaction);

            createLedgerEntry(transaction, virtualAccount, balanceUpdateResult.previousBalance());

            log.info("Top-up completed successfully for transaction: {}, new balance: {}",
                transaction.getTransactionRef(), balanceUpdateResult.newBalance());

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

    private void createLedgerEntry(Transaction transaction, VirtualAccount virtualAccount, java.math.BigDecimal balanceBefore) {
        var ledgerEntry = TransactionLedger.createCreditEntry(
            transaction.getId(),
            transaction.getTransactionRef(),
            virtualAccount.getWalletId(),
            virtualAccount.getUserId(),
            virtualAccount.getAmount(),
            balanceBefore,
            "Top-up via " + virtualAccount.getProvider().getDisplayName()
        );
        ledgerEntry.setPerformedByUserId(virtualAccount.getUserId());
        ledgerRepository.save(ledgerEntry);
        log.info("Created CREDIT ledger entry for transactionRef: {}", transaction.getTransactionRef());
    }

    private WalletAccessValidation validateWalletAccess(UUID userId, UUID walletId) {
        log.debug("Validating wallet access for user {} on wallet {}", userId, walletId);

        var roleValidationRequest = com.bni.orange.transaction.model.request.internal.RoleValidateRequest.builder()
            .walletId(walletId)
            .userId(userId)
            .action(com.bni.orange.transaction.model.enums.InternalAction.CREDIT)
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

        String currency = (String) roleValidation.extras().get("currency");

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
}
