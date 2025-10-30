package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.AuthServiceClient;
import com.bni.orange.transaction.client.UserServiceClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.config.properties.KafkaTopicProperties;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.event.EventPublisher;
import com.bni.orange.transaction.event.TransactionEventFactory;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.TransactionLedger;
import com.bni.orange.transaction.model.enums.InternalAction;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.request.RecipientLookupRequest;
import com.bni.orange.transaction.model.request.TransferConfirmRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.request.internal.BalanceValidateRequest;
import com.bni.orange.transaction.model.response.BalanceResponse;
import com.bni.orange.transaction.model.response.RecipientLookupResponse;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import com.bni.orange.transaction.model.response.WalletResolutionResponse;
import com.bni.orange.transaction.repository.TransactionLedgerRepository;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.utils.PhoneNumberUtils;
import com.bni.orange.transaction.utils.TransactionRefGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final AuthServiceClient authServiceClient;
    private final TransactionRefGenerator refGenerator;
    private final TransactionMapper transactionMapper;
    private final EventPublisher eventPublisher;
    private final KafkaTopicProperties topicProperties;
    private final QuickTransferService quickTransferService;

    @Value("${orange.transaction.fee.transfer:0}")
    private BigDecimal transferFee;

    public RecipientLookupResponse inquiry(RecipientLookupRequest request, UUID currentUserId, String accessToken) {
        log.info("Looking up recipient for phone: {}", request.phoneNumber());

        var normalizedPhone = PhoneNumberUtils.normalize(request.phoneNumber());

        if (!PhoneNumberUtils.isValid(normalizedPhone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_NUMBER, "Invalid phone number format: " + request.phoneNumber());
        }

        var user = Optional.ofNullable(userServiceClient.findByPhoneNumber(normalizedPhone, accessToken).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"));

        if (user.id().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Cannot transfer money to yourself");
        }

        WalletResolutionResponse resolvedWallet = null;
        try {
            resolvedWallet = walletServiceClient
                .getDefaultWalletByUserId(user.id())
                .block();

            if (resolvedWallet != null && resolvedWallet.walletId() == null) {
                log.warn("User {} has no default wallet configured", user.id());
                resolvedWallet = null;
            }
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.WALLET_NOT_FOUND) {
                log.warn("User {} has no default wallet configured", user.id());
            } else {
                throw ex;
            }
        }

        return buildLookupResponse(user, resolvedWallet, normalizedPhone);
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

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            var existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
            log.warn("Duplicate transaction detected, returning existing: {}", existing.getTransactionRef());
            return transactionMapper.toResponse(existing);
        }

        if (request.amount().compareTo(BigDecimal.ONE) < 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "Amount must be at least 1.00");
        }

        if (request.receiverUserId().equals(senderUserId)) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Cannot transfer to yourself");
        }

        validateSenderWalletAccess(senderUserId, request.senderWalletId(), request.amount());

        var totalAmount = request.amount().add(transferFee);

        var balanceValidationRequest = BalanceValidateRequest.builder()
            .walletId(request.senderWalletId())
            .amount(totalAmount)
            .action(InternalAction.DEBIT)
            .build();

        var balanceValidation = Optional.ofNullable(walletServiceClient.validateBalance(balanceValidationRequest).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to validate balance"));

        if (!balanceValidation.allowed()) {
            log.warn("Balance validation failed: walletId={}, code={}, message={}",
                request.senderWalletId(), balanceValidation.code(), balanceValidation.message());

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

        var receiverInfo = Optional.ofNullable(userServiceClient.findById(request.receiverUserId(), accessToken).block())
            .orElse(UserProfileResponse.builder()
                .id(request.receiverUserId())
                .name("Unknown")
                .phoneNumber("")
                .build());

        return createPendingTransaction(request, senderUserId, idempotencyKey, receiverInfo);
    }

    private void validateSenderWalletAccess(UUID userId, UUID walletId, BigDecimal amount) {
        log.debug("Validating wallet access for user {} on wallet {}", userId, walletId);

        var totalAmount = amount.add(transferFee);

        var roleValidationRequest = com.bni.orange.transaction.model.request.internal.RoleValidateRequest.builder()
            .walletId(walletId)
            .userId(userId)
            .action(InternalAction.DEBIT)
            .amount(totalAmount)
            .build();

        var roleValidation = Optional.ofNullable(walletServiceClient.validateRole(roleValidationRequest).block())
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

    @Transactional
    public TransactionResponse confirmTransfer(
        UUID transactionId,
        TransferConfirmRequest request,
        UUID currentUserId,
        String accessToken
    ) {
        log.info("Confirming transfer: {}", transactionId);

        var transaction = transactionRepository
            .findById(transactionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found: " + transactionId));

        if (!transaction.getSenderUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found or you don't have permission");
        }

        if (!transaction.getStatus().canBeProcessed()) {
            throw new BusinessException(ErrorCode.INVALID_TRANSACTION_STATE, "Transaction cannot be processed in current state: " + transaction.getStatus());
        }

        if (!Boolean.TRUE.equals(authServiceClient.verifyPin(request.pin(), accessToken).block())) {
            throw new BusinessException(ErrorCode.INVALID_PIN, "Invalid PIN provided");
        }

        transaction.markAsProcessing();
        transactionRepository.save(transaction);

        return executeTransfer(transaction);
    }

    private TransactionResponse executeTransfer(Transaction transaction) {
        log.info("Executing transfer: {}", transaction.getTransactionRef());
        BalanceResponse senderBalance;

        try {
            senderBalance = debitSender(transaction);
        } catch (Exception e) {
            handleTransferFailure(transaction, e);
            throw e;
        }

        try {
            var receiverBalance = creditReceiver(transaction);
            return finalizeSuccessfulTransfer(transaction, senderBalance, receiverBalance);
        } catch (Exception e) {
            log.error("Failed to credit receiver, reversing sender debit", e);
            reverseSenderDebit(transaction);
            handleTransferFailure(transaction, e);
            throw e;
        }
    }

    private BalanceResponse debitSender(Transaction transaction) {
        var balanceUpdateRequest = com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest.builder()
            .walletId(transaction.getSenderWalletId())
            .delta(transaction.getTotalAmount().negate())
            .referenceId(transaction.getIdempotencyKey() + "-sender")
            .reason("Transfer to " + transaction.getReceiverName())
            .build();

        var balanceUpdateResult = Optional.ofNullable(walletServiceClient.updateBalance(balanceUpdateRequest).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to debit sender"));

        if (!"OK".equals(balanceUpdateResult.code())) {
            throw new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, balanceUpdateResult.message());
        }

        log.debug("Sender debited successfully. New balance: {}", balanceUpdateResult.newBalance());

        return BalanceResponse.builder()
            .balance(balanceUpdateResult.newBalance())
            .balanceBefore(balanceUpdateResult.previousBalance())
            .balanceAfter(balanceUpdateResult.newBalance())
            .currency("IDR")
            .build();
    }

    private BalanceResponse creditReceiver(Transaction transaction) {
        var balanceUpdateRequest = com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest.builder()
            .walletId(transaction.getReceiverWalletId())
            .delta(transaction.getAmount())
            .referenceId(transaction.getIdempotencyKey() + "-receiver")
            .reason("Transfer from sender")
            .build();

        var balanceUpdateResult = Optional.ofNullable(walletServiceClient.updateBalance(balanceUpdateRequest).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to credit receiver"));

        if (!"OK".equals(balanceUpdateResult.code())) {
            throw new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, balanceUpdateResult.message());
        }

        log.debug("Receiver credited successfully. New balance: {}", balanceUpdateResult.newBalance());

        return BalanceResponse.builder()
            .balance(balanceUpdateResult.newBalance())
            .balanceBefore(balanceUpdateResult.previousBalance())
            .balanceAfter(balanceUpdateResult.newBalance())
            .currency("IDR")
            .build();
    }

    private void reverseSenderDebit(Transaction transaction) {
        log.warn("Reversing sender debit for transaction: {}", transaction.getTransactionRef());

        var balanceUpdateRequest = com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest.builder()
            .walletId(transaction.getSenderWalletId())
            .delta(transaction.getTotalAmount())
            .referenceId(transaction.getIdempotencyKey() + "-sender-reversal")
            .reason("Reversal for failed transfer: " + transaction.getTransactionRef())
            .build();

        try {
            var result = walletServiceClient.updateBalance(balanceUpdateRequest).block();
            if (result != null && "OK".equals(result.code())) {
                log.info("Sender debit reversed successfully. New balance: {}", result.newBalance());
            } else {
                log.error("CRITICAL: Reversal returned non-OK status: {}", result != null ? result.message() : "null");
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to reverse sender debit. Manual intervention required.", e);
        }
    }

    private TransactionResponse finalizeSuccessfulTransfer(
        Transaction transaction,
        BalanceResponse senderBalance,
        BalanceResponse receiverBalance
    ) {
        createLedgerEntries(transaction, senderBalance, receiverBalance, transaction.getSenderUserId());

        transaction.markAsSuccess();
        var savedTransaction = transactionRepository.save(transaction);

        quickTransferService.addOrUpdateFromTransaction(
            savedTransaction.getSenderUserId(),
            savedTransaction.getReceiverUserId(),
            savedTransaction.getReceiverName(),
            savedTransaction.getReceiverPhone()
        );

        var event = TransactionEventFactory.createTransactionCompletedEvent(savedTransaction);
        var topic = topicProperties.definitions().get("transaction-completed").name();
        eventPublisher.publish(topic, savedTransaction.getId().toString(), event);

        log.info("Transfer completed successfully: {}", transaction.getTransactionRef());
        return transactionMapper.toResponse(savedTransaction);
    }

    private void handleTransferFailure(Transaction transaction, Exception error) {
        log.error("Transfer failed: {}", transaction.getTransactionRef(), error);
        transaction.markAsFailed(error.getMessage());
        var failedTransaction = transactionRepository.save(transaction);
        var event = TransactionEventFactory.createTransactionFailedEvent(failedTransaction);
        var topic = topicProperties.definitions().get("transaction-failed").name();
        eventPublisher.publish(topic, failedTransaction.getId().toString(), event);
    }

    private void createLedgerEntries(
        Transaction transaction,
        BalanceResponse senderBalance,
        BalanceResponse receiverBalance,
        UUID performedByUserId
    ) {
        var senderEntry = TransactionLedger.createDebitEntry(
            transaction.getId(),
            transaction.getTransactionRef(),
            transaction.getSenderWalletId(),
            transaction.getSenderUserId(),
            transaction.getTotalAmount(),
            senderBalance.balanceBefore(),
            "Transfer to " + transaction.getReceiverName()
        );
        senderEntry.setPerformedByUserId(performedByUserId);

        var receiverEntry = TransactionLedger.createCreditEntry(
            transaction.getId(),
            transaction.getTransactionRef(),
            transaction.getReceiverWalletId(),
            transaction.getReceiverUserId(),
            transaction.getAmount(),
            receiverBalance.balanceBefore(),
            "Transfer from sender"
        );
        receiverEntry.setPerformedByUserId(null);

        ledgerRepository.save(senderEntry);
        ledgerRepository.save(receiverEntry);

        log.debug("Ledger entries created for transaction: {}, performedBy: {}",
            transaction.getTransactionRef(), performedByUserId);
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

    private TransactionResponse createPendingTransaction(
        TransferInitiateRequest request,
        UUID senderUserId,
        String idempotencyKey,
        UserProfileResponse receiverInfo
    ) {
        var transaction = Transaction.builder()
            .transactionRef(refGenerator.generate())
            .idempotencyKey(idempotencyKey)
            .type(TransactionType.TRANSFER_OUT)
            .status(TransactionStatus.PENDING)
            .amount(request.amount())
            .fee(transferFee)
            .currency(request.currency())
            .senderUserId(senderUserId)
            .senderWalletId(request.senderWalletId())
            .receiverUserId(request.receiverUserId())
            .receiverWalletId(request.receiverWalletId())
            .receiverName(receiverInfo.name())
            .receiverPhone(receiverInfo.phoneNumber())
            .notes(request.notes())
            .description("Transfer to " + receiverInfo.name())
            .build();

        transaction.calculateTotalAmount();

        var saved = transactionRepository.save(transaction);
        log.info("Pending transaction created: {}", saved.getTransactionRef());

        return transactionMapper.toResponse(saved);
    }
}
