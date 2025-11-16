package com.bni.orange.transaction.service.helper;

import com.bni.orange.splitbill.proto.PaymentStatusUpdatedEvent;
import com.bni.orange.transaction.config.properties.KafkaTopicProperties;
import com.bni.orange.transaction.event.EventPublisher;
import com.bni.orange.transaction.event.TransactionEventFactory;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.TransactionLedger;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.response.BalanceResponse;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.repository.TransactionLedgerRepository;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.service.QuickTransferService;
import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferFinalizer {

    private final TransactionRepository transactionRepository;
    private final TransactionLedgerRepository ledgerRepository;
    private final TransactionFactory transactionFactory;
    private final EventPublisher eventPublisher;
    private final KafkaTopicProperties topicProperties;
    private final QuickTransferService quickTransferService;
    private final TransactionMapper transactionMapper;

    public TransactionResponse finalizeSuccessfulTransfer(
        Transaction senderTransaction,
        BalanceResponse senderBalance,
        BalanceResponse receiverBalance
    ) {
        senderTransaction.markAsSuccess();
        var savedSenderTxn = transactionRepository.save(senderTransaction);

        var receiverTransaction = transactionFactory.createReceiverTransaction(senderTransaction, TransactionType.TRANSFER_IN);
        receiverTransaction.markAsSuccess();
        var savedReceiverTxn = transactionRepository.save(receiverTransaction);

        createLedgerEntries(savedSenderTxn, savedReceiverTxn, senderBalance, receiverBalance, savedSenderTxn.getUserId());

        quickTransferService.addOrUpdateFromTransaction(
            savedSenderTxn.getUserId(),
            savedSenderTxn.getCounterpartyUserId(),
            savedSenderTxn.getCounterpartyName(),
            savedSenderTxn.getCounterpartyPhone()
        );

        publishCompletionEvents(savedSenderTxn, savedReceiverTxn);

        if (savedSenderTxn.getSplitBillId() != null) {
            publishSplitBillPaymentEvent(savedSenderTxn);
        }

        log.info("Transfer completed successfully: ref={} (sender_id: {}, receiver_id: {})",
            savedSenderTxn.getTransactionRef(), savedSenderTxn.getId(), savedReceiverTxn.getId());
        return transactionMapper.toResponse(savedSenderTxn);
    }

    public TransactionResponse finalizeSuccessfulInternalTransfer(
        Transaction senderTransaction,
        BalanceResponse senderBalance,
        BalanceResponse receiverBalance
    ) {
        senderTransaction.markAsSuccess();
        var savedSenderTxn = transactionRepository.save(senderTransaction);

        var receiverTransaction = transactionFactory.createReceiverTransaction(senderTransaction, TransactionType.INTERNAL_TRANSFER_IN);
        receiverTransaction.markAsSuccess();
        var savedReceiverTxn = transactionRepository.save(receiverTransaction);

        createLedgerEntries(savedSenderTxn, savedReceiverTxn, senderBalance, receiverBalance, savedSenderTxn.getUserId());

        publishCompletionEvents(savedSenderTxn, savedReceiverTxn);

        log.info("Internal transfer completed successfully: ref={} (sender_id: {}, receiver_id: {})",
            savedSenderTxn.getTransactionRef(), savedSenderTxn.getId(), savedReceiverTxn.getId());
        return transactionMapper.toResponse(savedSenderTxn);
    }

    private void publishCompletionEvents(Transaction senderTxn, Transaction receiverTxn) {
        var senderEvent = TransactionEventFactory.createTransactionCompletedEvent(senderTxn);
        var receiverEvent = TransactionEventFactory.createTransactionCompletedEvent(receiverTxn);
        var topic = topicProperties.definitions().get("transaction-completed").name();
        eventPublisher.publish(topic, senderTxn.getId().toString(), senderEvent);
        eventPublisher.publish(topic, receiverTxn.getId().toString(), receiverEvent);
    }

    public void handleTransferFailure(Transaction transaction, Exception error) {
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
}
