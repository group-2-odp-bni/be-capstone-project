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
        var builder = TransactionCompletedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setType(transaction.getType().name())
            .setStatus(transaction.getStatus().name())
            .setAmount(transaction.getAmount().toString())
            .setCurrency(transaction.getCurrency())
            .setUserId(transaction.getUserId().toString())
            .setWalletId(transaction.getWalletId().toString())
            .setDescription(transaction.getDescription() != null ? transaction.getDescription() : "")
            .setNotes(transaction.getNotes() != null ? transaction.getNotes() : "")
            .setTimestamp(getTimestamp(transaction));

        if (transaction.getCounterpartyUserId() != null) {
            builder.setCounterpartyUserId(transaction.getCounterpartyUserId().toString());
        }
        if (transaction.getCounterpartyWalletId() != null) {
            builder.setCounterpartyWalletId(transaction.getCounterpartyWalletId().toString());
        }
        if (transaction.getCounterpartyName() != null) {
            builder.setCounterpartyName(transaction.getCounterpartyName());
        }
        if (transaction.getCounterpartyPhone() != null) {
            builder.setCounterpartyPhone(transaction.getCounterpartyPhone());
        }

        return builder.build();
    }

    public static TransactionFailedEvent createTransactionFailedEvent(Transaction transaction) {
        var builder = TransactionFailedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setType(transaction.getType().name())
            .setStatus(transaction.getStatus().name())
            .setAmount(transaction.getAmount().toString())
            .setCurrency(transaction.getCurrency())
            .setUserId(transaction.getUserId().toString())
            .setWalletId(transaction.getWalletId().toString())
            .setDescription(transaction.getDescription() != null ? transaction.getDescription() : "")
            .setNotes(transaction.getNotes() != null ? transaction.getNotes() : "")
            .setTimestamp(getTimestamp(transaction))
            .setFailureReason(transaction.getFailureReason() != null ? transaction.getFailureReason() : "");

        if (transaction.getCounterpartyUserId() != null) {
            builder.setCounterpartyUserId(transaction.getCounterpartyUserId().toString());
        }
        if (transaction.getCounterpartyWalletId() != null) {
            builder.setCounterpartyWalletId(transaction.getCounterpartyWalletId().toString());
        }
        if (transaction.getCounterpartyName() != null) {
            builder.setCounterpartyName(transaction.getCounterpartyName());
        }
        if (transaction.getCounterpartyPhone() != null) {
            builder.setCounterpartyPhone(transaction.getCounterpartyPhone());
        }

        return builder.build();
    }

    private static long getTimestamp(Transaction transaction) {
        return transaction.getCompletedAt() != null
            ? transaction.getCompletedAt().toInstant().toEpochMilli()
            : transaction.getCreatedAt().toInstant().toEpochMilli();
    }
}
