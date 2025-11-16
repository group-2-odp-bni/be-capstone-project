package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.AuthServiceClient;
import com.bni.orange.transaction.client.UserServiceClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.config.properties.KafkaTopicProperties;
import com.bni.orange.transaction.config.properties.TransferLimitProperties;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.event.EventPublisher;
import com.bni.orange.transaction.event.TransactionEventFactory;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.TransactionLedger;
import com.bni.orange.transaction.model.enums.InternalAction;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.enums.TransferType;
import com.bni.orange.transaction.model.request.InternalTransferRequest;
import com.bni.orange.transaction.model.request.RecipientLookupRequest;
import com.bni.orange.transaction.model.request.TransferConfirmRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.transaction.model.request.internal.BalanceValidateRequest;
import com.bni.orange.transaction.model.request.internal.RoleValidateRequest;
import com.bni.orange.transaction.model.request.internal.ValidateWalletOwnershipRequest;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bni.orange.splitbill.proto.PaymentStatusUpdatedEvent;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

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
    private final Executor virtualThreadTaskExecutor;
    private final TransferLimitProperties transferLimitProperties;

    @Value("${orange.transaction.fee.transfer:0}")
    private BigDecimal transferFee;

    private <T> CompletableFuture<T> supplyAsyncWithContext(Supplier<T> supplier) {
        var context = SecurityContextHolder.getContext();
        return CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.setContext(context);
            try {
                return supplier.get();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, virtualThreadTaskExecutor);
    }

    private CompletableFuture<Void> runAsyncWithContext(Runnable runnable) {
        var context = SecurityContextHolder.getContext();
        return CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(context);
            try {
                runnable.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, virtualThreadTaskExecutor);
    }

    public RecipientLookupResponse inquiry(RecipientLookupRequest request, UUID currentUserId, String accessToken) {
        log.info("Looking up recipient for phone: {}", request.phoneNumber());

        var normalizedPhone = PhoneNumberUtils.normalize(request.phoneNumber());

        if (!PhoneNumberUtils.isValid(normalizedPhone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_NUMBER, "Invalid phone number format: " + request.phoneNumber());
        }

        var userFuture = supplyAsyncWithContext(() -> Optional
            .ofNullable(userServiceClient.findByPhoneNumber(normalizedPhone, accessToken).block())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"))
        );

        try {
            var user = userFuture.join();

            if (user.id().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Cannot transfer money to yourself");
            }

            var walletFuture = supplyAsyncWithContext(() -> {
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

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            var existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
            log.warn("Duplicate transaction detected, returning existing: {}", existing.getTransactionRef());
            return transactionMapper.toResponse(existing);
        }

        if (request.amount().compareTo(transferLimitProperties.minAmount()) < 0 ||
            request.amount().compareTo(transferLimitProperties.maxAmount()) > 0) {
            throw new BusinessException(
                ErrorCode.INVALID_AMOUNT,
                "Transfer amount must be between %s and %s".formatted(transferLimitProperties.minAmount(), transferLimitProperties.maxAmount())
            );
        }

        if (request.receiverUserId().equals(senderUserId)) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Cannot transfer to yourself");
        }

        var totalAmount = request.amount().add(transferFee);

        var walletAccessValidationFuture = runAsyncWithContext(() ->
            validateSenderWalletAccess(senderUserId, request.senderWalletId(), request.amount(), TransferType.EXTERNAL)
        );

        var balanceValidationFuture = supplyAsyncWithContext(() -> {
            var balanceValidationRequest = BalanceValidateRequest.builder()
                .walletId(request.senderWalletId())
                .amount(totalAmount)
                .action(InternalAction.DEBIT)
                .actorUserId(senderUserId)
                .build();

            return Optional
                .ofNullable(walletServiceClient.validateBalance(balanceValidationRequest).block())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to validate balance"));
        });

        var receiverInfoFuture = supplyAsyncWithContext(() ->
            Optional
                .ofNullable(userServiceClient.findById(request.receiverUserId(), accessToken).block())
                .orElse(UserProfileResponse.builder()
                    .id(request.receiverUserId())
                    .name("Unknown")
                    .phoneNumber("")
                    .build())
        );

        var senderInfoFuture = supplyAsyncWithContext(() ->
            Optional.ofNullable(userServiceClient.findById(senderUserId, accessToken).block())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Sender user not found"))
        );

        try {
            CompletableFuture.allOf(walletAccessValidationFuture, balanceValidationFuture, receiverInfoFuture, senderInfoFuture).join();

            var balanceValidation = balanceValidationFuture.join();

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

            var receiverInfo = receiverInfoFuture.join();
            var senderInfo = senderInfoFuture.join();
            return createPendingTransaction(request, senderUserId, idempotencyKey, senderInfo, receiverInfo);

        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during parallel transfer validation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void validateSenderWalletAccess(UUID userId, UUID walletId, BigDecimal amount, TransferType transferType) {
        log.debug("Validating wallet access for user {} on wallet {}", userId, walletId);

        var totalAmount = amount.add(transferFee);

        var roleValidationRequest = RoleValidateRequest.builder()
            .walletId(walletId)
            .userId(userId)
            .action(InternalAction.DEBIT)
            .amount(totalAmount)
            .transferType(transferType)
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

        var transactionFuture = supplyAsyncWithContext(() ->
            transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found: " + transactionId))
        );

        var pinVerificationFuture = supplyAsyncWithContext(() ->
            Boolean.TRUE.equals(authServiceClient.verifyPin(request.pin(), accessToken).block())
        );

        try {
            CompletableFuture.allOf(transactionFuture, pinVerificationFuture).join();

            var transaction = transactionFuture.join();
            var isPinValid = pinVerificationFuture.join();

            if (!transaction.getUserId().equals(currentUserId)) {
                throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found or you don't have permission");
            }

            if (!transaction.getStatus().canBeProcessed()) {
                throw new BusinessException(ErrorCode.INVALID_TRANSACTION_STATE, "Transaction cannot be processed in current state: " + transaction.getStatus());
            }

            if (!isPinValid) {
                throw new BusinessException(ErrorCode.INVALID_PIN, "Invalid PIN provided");
            }

            transaction.markAsProcessing();
            transactionRepository.save(transaction);

            return executeTransfer(transaction);

        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during transfer confirmation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
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

    private TransactionResponse executeInternalTransferSaga(Transaction transaction) {
        log.info("Executing internal transfer saga: {}", transaction.getTransactionRef());
        BalanceResponse senderBalance;
        BalanceResponse receiverBalance;
        var senderDebited = false;
        var receiverCredited = false;

        try {
            senderBalance = debitSender(transaction);
            senderDebited = true;
            log.info("Step 1/3: Sender debited successfully for internal transfer: {}", transaction.getTransactionRef());

            receiverBalance = creditReceiver(transaction);
            receiverCredited = true;
            log.info("Step 2/3: Receiver credited successfully for internal transfer: {}", transaction.getTransactionRef());

            var result = finalizeSuccessfulInternalTransfer(transaction, senderBalance, receiverBalance);
            log.info("Step 3/3: Internal transfer finalized successfully: {}", transaction.getTransactionRef());
            return result;

        } catch (Exception e) {
            log.error("Internal transfer saga failed at some step, initiating rollback: {}", transaction.getTransactionRef(), e);

            if (receiverCredited) {
                log.warn("Rolling back receiver credit for transaction: {}", transaction.getTransactionRef());
                reverseReceiverCredit(transaction);
            }

            if (senderDebited) {
                log.warn("Rolling back sender debit for transaction: {}", transaction.getTransactionRef());
                reverseSenderDebit(transaction);
            }

            handleTransferFailure(transaction, e);
            throw e;
        }
    }

    private BalanceResponse debitSender(Transaction transaction) {
        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getWalletId())
            .delta(transaction.getTotalAmount().negate())
            .referenceId(transaction.getIdempotencyKey() + "-sender")
            .reason("Transfer to " + transaction.getCounterpartyName())
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
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
        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getCounterpartyWalletId())
            .delta(transaction.getAmount())
            .referenceId(transaction.getIdempotencyKey() + "-receiver")
            .reason("Transfer from sender")
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
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

        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getWalletId())
            .delta(transaction.getTotalAmount())
            .referenceId(transaction.getIdempotencyKey() + "-sender-reversal")
            .reason("Reversal for failed transfer: " + transaction.getTransactionRef())
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
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

    private void reverseReceiverCredit(Transaction transaction) {
        log.warn("Reversing receiver credit for transaction: {}", transaction.getTransactionRef());

        var transferType = transaction.getType().isTransfer() &&
            (transaction.getType() == TransactionType.INTERNAL_TRANSFER_OUT ||
             transaction.getType() == TransactionType.INTERNAL_TRANSFER_IN)
            ? TransferType.INTERNAL
            : TransferType.EXTERNAL;

        var balanceUpdateRequest = BalanceUpdateRequest.builder()
            .walletId(transaction.getCounterpartyWalletId())
            .delta(transaction.getAmount().negate())
            .referenceId(transaction.getIdempotencyKey() + "-receiver-reversal")
            .reason("Reversal for failed transfer: " + transaction.getTransactionRef())
            .actorUserId(transaction.getUserId())
            .transferType(transferType)
            .build();

        try {
            var result = walletServiceClient.updateBalance(balanceUpdateRequest).block();
            if (result != null && "OK".equals(result.code())) {
                log.info("Receiver credit reversed successfully. New balance: {}", result.newBalance());
            } else {
                log.error("CRITICAL: Receiver reversal returned non-OK status: {}", result != null ? result.message() : "null");
            }
        } catch (Exception e) {
            log.error("CRITICAL: Failed to reverse receiver credit. Manual intervention required.", e);
        }
    }

    private TransactionResponse finalizeSuccessfulTransfer(
        Transaction senderTransaction,
        BalanceResponse senderBalance,
        BalanceResponse receiverBalance
    ) {
        senderTransaction.markAsSuccess();
        var savedSenderTxn = transactionRepository.save(senderTransaction);

        var receiverTransaction = createReceiverTransaction(senderTransaction, TransactionType.TRANSFER_IN);
        receiverTransaction.markAsSuccess();
        var savedReceiverTxn = transactionRepository.save(receiverTransaction);

        createLedgerEntries(savedSenderTxn, savedReceiverTxn, senderBalance, receiverBalance, savedSenderTxn.getUserId());

        quickTransferService.addOrUpdateFromTransaction(
            savedSenderTxn.getUserId(),
            savedSenderTxn.getCounterpartyUserId(),
            savedSenderTxn.getCounterpartyName(),
            savedSenderTxn.getCounterpartyPhone()
        );

        var senderEvent = TransactionEventFactory.createTransactionCompletedEvent(savedSenderTxn);
        var receiverEvent = TransactionEventFactory.createTransactionCompletedEvent(savedReceiverTxn);
        var topic = topicProperties.definitions().get("transaction-completed").name();
        eventPublisher.publish(topic, savedSenderTxn.getId().toString(), senderEvent);
        eventPublisher.publish(topic, savedReceiverTxn.getId().toString(), receiverEvent);

        if (savedSenderTxn.getSplitBillId() != null) {
            publishSplitBillPaymentEvent(savedSenderTxn);
        }

        log.info("Transfer completed successfully: ref={} (sender_id: {}, receiver_id: {})",
            savedSenderTxn.getTransactionRef(), savedSenderTxn.getId(), savedReceiverTxn.getId());
        return transactionMapper.toResponse(savedSenderTxn);
    }

    private void publishSplitBillPaymentEvent(Transaction transaction) {
        var eventBuilder = PaymentStatusUpdatedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setBillId(transaction.getSplitBillId())
            .setMemberId(transaction.getSplitBillMemberId())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setStatus("CAPTURED")
            .setPaidAt(Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()).setNanos(Instant.now().getNano()));

        if (transaction.getAmount() != null) {
            eventBuilder.setAmount(transaction.getAmount().longValue());
        }

        var event = eventBuilder.build();
        var topic = topicProperties.definitions().get("payment-status-updated").name();
        eventPublisher.publish(topic, transaction.getSplitBillId(), event);
        log.info("Published {} for Split Bill: billId={}, memberId={}, txnRef={}",
            topic, transaction.getSplitBillId(), transaction.getSplitBillMemberId(), transaction.getTransactionRef());
    }

    private void publishSplitBillPaymentFailureEvent(Transaction transaction, String failureReason) {
        var eventBuilder = PaymentStatusUpdatedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setBillId(transaction.getSplitBillId())
            .setMemberId(transaction.getSplitBillMemberId())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setStatus("FAILED")
            .setFailureReason(failureReason != null ? failureReason : "Transaction failed");

        if (transaction.getAmount() != null) {
            eventBuilder.setAmount(transaction.getAmount().longValue());
        }

        var event = eventBuilder.build();
        var topic = topicProperties.definitions().get("payment-status-updated").name();
        eventPublisher.publish(topic, transaction.getSplitBillId(), event);
        log.warn("Published {} with FAILED status for Split Bill: billId={}, memberId={}, txnRef={}, reason={}",
            topic, transaction.getSplitBillId(), transaction.getSplitBillMemberId(),
            transaction.getTransactionRef(), failureReason);
    }

    private TransactionResponse finalizeSuccessfulInternalTransfer(
        Transaction senderTransaction,
        BalanceResponse senderBalance,
        BalanceResponse receiverBalance
    ) {
        senderTransaction.markAsSuccess();
        var savedSenderTxn = transactionRepository.save(senderTransaction);

        var receiverTransaction = createReceiverTransaction(senderTransaction, TransactionType.INTERNAL_TRANSFER_IN);
        receiverTransaction.markAsSuccess();
        var savedReceiverTxn = transactionRepository.save(receiverTransaction);

        createLedgerEntries(savedSenderTxn, savedReceiverTxn, senderBalance, receiverBalance, savedSenderTxn.getUserId());

        var senderEvent = TransactionEventFactory.createTransactionCompletedEvent(savedSenderTxn);
        var receiverEvent = TransactionEventFactory.createTransactionCompletedEvent(savedReceiverTxn);
        var topic = topicProperties.definitions().get("transaction-completed").name();
        eventPublisher.publish(topic, savedSenderTxn.getId().toString(), senderEvent);
        eventPublisher.publish(topic, savedReceiverTxn.getId().toString(), receiverEvent);

        log.info("Internal transfer completed successfully: ref={} (sender_id: {}, receiver_id: {})",
            savedSenderTxn.getTransactionRef(), savedSenderTxn.getId(), savedReceiverTxn.getId());
        return transactionMapper.toResponse(savedSenderTxn);
    }

    private Transaction createReceiverTransaction(Transaction senderTransaction, TransactionType type) {
        return Transaction.builder()
            .transactionRef(senderTransaction.getTransactionRef())
            .idempotencyKey(senderTransaction.getIdempotencyKey() + "-receiver")
            .type(type)
            .status(TransactionStatus.PROCESSING)
            .amount(senderTransaction.getAmount())
            .fee(BigDecimal.ZERO)
            .totalAmount(senderTransaction.getAmount())
            .currency(senderTransaction.getCurrency())
            .userId(senderTransaction.getCounterpartyUserId())
            .userName(senderTransaction.getCounterpartyName())
            .userPhone(senderTransaction.getCounterpartyPhone())
            .walletId(senderTransaction.getCounterpartyWalletId())
            .counterpartyUserId(senderTransaction.getUserId())
            .counterpartyWalletId(senderTransaction.getWalletId())
            .counterpartyName(senderTransaction.getUserName())
            .counterpartyPhone(senderTransaction.getUserPhone())
            .notes(senderTransaction.getNotes())
            .description("Transfer from " + senderTransaction.getUserName())
            .splitBillId(senderTransaction.getSplitBillId())
            .splitBillMemberId(senderTransaction.getSplitBillMemberId())
            .build();
    }

    private void handleTransferFailure(Transaction transaction, Exception error) {
        log.error("Transfer failed: {}", transaction.getTransactionRef(), error);
        transaction.markAsFailed(error.getMessage());
        var failedTransaction = transactionRepository.save(transaction);

        var event = TransactionEventFactory.createTransactionFailedEvent(failedTransaction);
        var topic = topicProperties.definitions().get("transaction-failed").name();
        eventPublisher.publish(topic, failedTransaction.getId().toString(), event);

        if (failedTransaction.getSplitBillId() != null) {
            publishSplitBillPaymentFailureEvent(failedTransaction, error.getMessage());
        }
    }

    private void createLedgerEntries(
        Transaction senderTransaction,
        Transaction receiverTransaction,
        BalanceResponse senderBalance,
        BalanceResponse receiverBalance,
        UUID performedByUserId
    ) {
        var senderEntry = TransactionLedger.createDebitEntry(
            senderTransaction.getId(),
            senderTransaction.getTransactionRef(),
            senderTransaction.getWalletId(),
            senderTransaction.getUserId(),
            senderTransaction.getTotalAmount(),
            senderBalance.balanceBefore(),
            "Transfer to " + senderTransaction.getCounterpartyName()
        );
        senderEntry.setPerformedByUserId(performedByUserId);

        var receiverEntry = TransactionLedger.createCreditEntry(
            receiverTransaction.getId(),
            receiverTransaction.getTransactionRef(),
            receiverTransaction.getWalletId(),
            receiverTransaction.getUserId(),
            receiverTransaction.getAmount(),
            receiverBalance.balanceBefore(),
            "Transfer from sender"
        );
        receiverEntry.setPerformedByUserId(null);

        ledgerRepository.save(senderEntry);
        ledgerRepository.save(receiverEntry);

        log.debug("Ledger entries created for ref: {}, performedBy: {}",
            senderTransaction.getTransactionRef(), performedByUserId);
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
        UserProfileResponse senderInfo,
        UserProfileResponse receiverInfo
    ) {
        var transactionRef = refGenerator.generate();

        var senderTransaction = Transaction.builder()
            .transactionRef(transactionRef)
            .idempotencyKey(idempotencyKey)
            .type(TransactionType.TRANSFER_OUT)
            .status(TransactionStatus.PENDING)
            .amount(request.amount())
            .fee(transferFee)
            .currency(request.currency())
            .userId(senderUserId)
            .userName(senderInfo.name())
            .userPhone(senderInfo.phoneNumber())
            .walletId(request.senderWalletId())
            .counterpartyUserId(request.receiverUserId())
            .counterpartyWalletId(request.receiverWalletId())
            .counterpartyName(receiverInfo.name())
            .counterpartyPhone(receiverInfo.phoneNumber())
            .notes(request.notes())
            .description("Transfer to " + receiverInfo.name())
            .splitBillId(request.splitBillId())
            .splitBillMemberId(request.splitBillMemberId())
            .build();

        senderTransaction.calculateTotalAmount();

        var savedSender = transactionRepository.save(senderTransaction);
        log.info("Pending sender transaction created: {}", savedSender.getTransactionRef());

        return transactionMapper.toResponse(savedSender);
    }

    private Transaction createAndSavePendingInternalTransaction(
        InternalTransferRequest request,
        UUID userId,
        String idempotencyKey,
        String sourceWalletName,
        String destinationWalletName
    ) {
        var transactionRef = refGenerator.generate();

        var transaction = Transaction.builder()
            .transactionRef(transactionRef)
            .idempotencyKey(idempotencyKey)
            .type(TransactionType.INTERNAL_TRANSFER_OUT)
            .status(TransactionStatus.PENDING)
            .amount(request.amount())
            .fee(BigDecimal.ZERO)
            .currency("IDR")
            .userId(userId)
            .userName(sourceWalletName)
            .userPhone(null)
            .walletId(request.sourceWalletId())
            .counterpartyUserId(userId)
            .counterpartyWalletId(request.destinationWalletId())
            .counterpartyName(destinationWalletName)
            .counterpartyPhone(null)
            .notes("Internal Transfer")
            .description("Transfer from " + sourceWalletName + " to " + destinationWalletName)
            .build();

        transaction.calculateTotalAmount();

        var savedTransaction = transactionRepository.save(transaction);
        log.info("Pending internal transaction created: {} (from {} to {})",
            savedTransaction.getTransactionRef(), sourceWalletName, destinationWalletName);

        return savedTransaction;
    }

    @Transactional
    public TransactionResponse initiateInternalTransfer(
        InternalTransferRequest request,
        UUID userId,
        String idempotencyKey
    ) {
        log.info("Initiating internal transfer from user {}, from wallet {} to wallet {}, amount: {}",
            userId, request.sourceWalletId(), request.destinationWalletId(), request.amount());

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            var existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));
            log.warn("Duplicate internal transfer detected, returning existing: {}", existing.getTransactionRef());
            return transactionMapper.toResponse(existing);
        }

        if (request.sourceWalletId().equals(request.destinationWalletId())) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Source and destination wallets cannot be the same");
        }

        var ownershipValidationFuture = supplyAsyncWithContext(() -> {
            var req = ValidateWalletOwnershipRequest.of(userId, List.of(request.sourceWalletId(), request.destinationWalletId()));
            var res = walletServiceClient.validateWalletOwnership(req).block();
            if (res == null || !res.isOwner()) {
                throw new BusinessException(ErrorCode.WALLET_ACCESS_DENIED, "User does not own one or both wallets");
            }
            return res; // Return the full response to get wallet names
        });

        var totalAmount = request.amount();

        var walletAccessValidationFuture = runAsyncWithContext(() ->
            validateSenderWalletAccess(userId, request.sourceWalletId(), request.amount(), TransferType.INTERNAL)
        );

        var balanceValidationFuture = supplyAsyncWithContext(() -> {
            var balanceValidationRequest = BalanceValidateRequest.builder()
                .walletId(request.sourceWalletId())
                .amount(totalAmount)
                .action(InternalAction.DEBIT)
                .actorUserId(userId)
                .build();

            return Optional
                .ofNullable(walletServiceClient.validateBalance(balanceValidationRequest).block())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_SERVICE_ERROR, "Failed to validate balance"));
        });

        try {
            CompletableFuture.allOf(ownershipValidationFuture, walletAccessValidationFuture, balanceValidationFuture).join();

            var balanceValidation = balanceValidationFuture.join();

            if (!balanceValidation.allowed()) {
                log.warn("Balance validation failed: walletId={}, code={}, message={}",
                    request.sourceWalletId(), balanceValidation.code(), balanceValidation.message());

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

            var ownershipValidation = ownershipValidationFuture.join();
            var sourceWalletName = ownershipValidation.walletNames().getOrDefault(request.sourceWalletId(), "Unknown Wallet");
            var destinationWalletName = ownershipValidation.walletNames().getOrDefault(request.destinationWalletId(), "Unknown Wallet");

            var pendingTransaction = createAndSavePendingInternalTransaction(
                request,
                userId,
                idempotencyKey,
                sourceWalletName,
                destinationWalletName
            );

            pendingTransaction.markAsProcessing();
            var processingTransaction = transactionRepository.save(pendingTransaction);

            return executeInternalTransferSaga(processingTransaction);
        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during parallel internal transfer validation", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
