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
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.enums.WalletPermission;
import com.bni.orange.transaction.model.request.BalanceAdjustmentRequest;
import com.bni.orange.transaction.model.request.RecipientLookupRequest;
import com.bni.orange.transaction.model.request.TransferConfirmRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.response.BalanceResponse;
import com.bni.orange.transaction.model.response.RecipientLookupResponse;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import com.bni.orange.transaction.model.response.WalletAccessValidation;
import com.bni.orange.transaction.model.response.WalletResolutionResponse;
import com.bni.orange.transaction.model.response.WalletResponse;
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

    public RecipientLookupResponse lookupRecipient(
        RecipientLookupRequest request,
        UUID currentUserId,
        String accessToken
    ) {
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
                .resolveRecipientWallet(normalizedPhone, "PHONE", "IDR")
                .block();
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.RECIPIENT_WALLET_NOT_FOUND) {
                log.warn("Recipient {} has no default wallet configured", normalizedPhone);
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

        var balance = Optional.ofNullable(walletServiceClient.getBalance(request.senderWalletId()).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to fetch wallet balance"));

        var totalAmount = request.amount().add(transferFee);
        if (balance.balance().compareTo(totalAmount) < 0) {
            throw new BusinessException(
                ErrorCode.INSUFFICIENT_BALANCE,
                String.format("Insufficient balance. Required: %s, Available: %s", totalAmount, balance.balance())
            );
        }

        var receiverInfo = Optional.ofNullable(userServiceClient
                .findByPhoneNumber(PhoneNumberUtils.normalize("+62" + request.receiverUserId()), accessToken)
                .block())
            .orElse(UserProfileResponse.builder()
                .id(request.receiverUserId())
                .name("Unknown")
                .phoneNumber("")
                .build());

        return createPendingTransaction(request, senderUserId, idempotencyKey, receiverInfo);
    }

    private void validateSenderWalletAccess(UUID userId, UUID walletId, BigDecimal amount) {
        log.debug("Validating wallet access for user {} on wallet {}", userId, walletId);

        var validation = Optional.ofNullable(walletServiceClient
                .validateAccess(walletId, userId, WalletPermission.TRANSACT)
                .block())
            .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR,
                "Failed to validate wallet access"));

        if (!validation.hasAccess()) {
            log.warn("Wallet access denied: userId={}, walletId={}, reason={}",
                userId, walletId, validation.denialReason());
            throw new BusinessException(ErrorCode.WALLET_ACCESS_DENIED, validation.denialReason());
        }

        if (validation.walletStatus() == null || !"ACTIVE".equals(validation.walletStatus().name())) {
            throw new BusinessException(ErrorCode.WALLET_NOT_ACTIVE,
                "Wallet is not active: " + validation.walletStatus());
        }

        if (validation.spendingLimit() != null) {
            var totalAmount = amount.add(transferFee);
            if (totalAmount.compareTo(validation.spendingLimit()) > 0) {
                log.warn("Spending limit exceeded: userId={}, walletId={}, amount={}, limit={}",
                    userId, walletId, totalAmount, validation.spendingLimit());
                throw new BusinessException(ErrorCode.SPENDING_LIMIT_EXCEEDED,
                    String.format("Transaction amount %s exceeds your spending limit of %s for this wallet",
                        totalAmount, validation.spendingLimit()));
            }
        }

        log.debug("Wallet access validated successfully: userId={}, walletId={}, role={}",
            userId, walletId, validation.userRole());
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
        var debitRequest = BalanceAdjustmentRequest.builder()
            .amount(transaction.getTotalAmount().negate())
            .reason("TRANSFER_OUT")
            .description("Transfer to " + transaction.getReceiverName())
            .build();

        var senderBalance = Optional.ofNullable(walletServiceClient.adjustBalance(
                transaction.getSenderWalletId(),
                debitRequest,
                transaction.getIdempotencyKey() + "-sender"
            ).block()
        ).orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to debit sender"));

        log.debug("Sender debited successfully. New balance: {}", senderBalance.balanceAfter());
        return senderBalance;
    }

    private BalanceResponse creditReceiver(Transaction transaction) {
        var creditRequest = BalanceAdjustmentRequest.builder()
            .amount(transaction.getAmount())
            .reason("TRANSFER_IN")
            .description("Transfer from sender")
            .build();

        var receiverBalance = Optional.ofNullable(walletServiceClient.adjustBalance(
                transaction.getReceiverWalletId(),
                creditRequest,
                transaction.getIdempotencyKey() + "-receiver"
            ).block()
        ).orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to credit receiver"));

        log.debug("Receiver credited successfully. New balance: {}", receiverBalance.balanceAfter());
        return receiverBalance;
    }

    private void reverseSenderDebit(Transaction transaction) {
        log.warn("Reversing sender debit for transaction: {}", transaction.getTransactionRef());

        var reversalRequest = BalanceAdjustmentRequest.builder()
            .amount(transaction.getTotalAmount())
            .reason("REVERSAL")
            .description("Reversal for failed transfer: " + transaction.getTransactionRef())
            .build();

        try {
            walletServiceClient.adjustBalance(
                transaction.getSenderWalletId(),
                reversalRequest,
                transaction.getIdempotencyKey() + "-sender-reversal"
            ).block();
            log.info("Sender debit reversed successfully");
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
