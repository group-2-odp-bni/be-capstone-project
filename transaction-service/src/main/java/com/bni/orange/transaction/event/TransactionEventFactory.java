package com.bni.orange.transaction.event;

import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.proto.TransactionCompletedEvent;
import com.bni.orange.transaction.proto.TransactionFailedEvent;

import java.util.UUID;

public final class TransactionEventFactory {

    private TransactionEventFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static TransactionCompletedEvent createTransactionCompletedEvent(Transaction transaction) {
        return TransactionCompletedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setType(transaction.getType().name())
            .setStatus(transaction.getStatus().name())
            .setAmount(transaction.getAmount().toString())
            .setCurrency(transaction.getCurrency())
            .setSenderUserId(transaction.getSenderUserId().toString())
            .setSenderWalletId(transaction.getSenderWalletId().toString())
            .setReceiverUserId(transaction.getReceiverUserId().toString())
            .setReceiverWalletId(transaction.getReceiverWalletId().toString())
            .setReceiverName(transaction.getReceiverName())
            .setReceiverPhone(transaction.getReceiverPhone())
            .setDescription(transaction.getDescription())
            .setNotes(transaction.getNotes())
            .setTimestamp(getTimestamp(transaction))
            .build();
    }

    public static TransactionFailedEvent createTransactionFailedEvent(Transaction transaction) {
        return TransactionFailedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setType(transaction.getType().name())
            .setStatus(transaction.getStatus().name())
            .setAmount(transaction.getAmount().toString())
            .setCurrency(transaction.getCurrency())
            .setSenderUserId(transaction.getSenderUserId().toString())
            .setSenderWalletId(transaction.getSenderWalletId().toString())
            .setReceiverUserId(transaction.getReceiverUserId().toString())
            .setReceiverWalletId(transaction.getReceiverWalletId().toString())
            .setReceiverName(transaction.getReceiverName())
            .setReceiverPhone(transaction.getReceiverPhone())
            .setDescription(transaction.getDescription())
            .setNotes(transaction.getNotes())
            .setTimestamp(getTimestamp(transaction))
            .setFailureReason(transaction.getFailureReason())
            .build();
    }

    private static long getTimestamp(Transaction transaction) {
        return transaction.getCompletedAt() != null
            ? transaction.getCompletedAt().toInstant().toEpochMilli()
            : transaction.getCreatedAt().toInstant().toEpochMilli();
    }
}
