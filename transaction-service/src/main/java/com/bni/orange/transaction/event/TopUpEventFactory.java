package com.bni.orange.transaction.event;

import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class TopUpEventFactory {

    public TopUpEvents.TopUpInitiated createTopUpInitiatedEvent(
        Transaction transaction,
        VirtualAccount virtualAccount
    ) {
        log.debug("Creating TOP_UP_INITIATED event for transaction: {}", transaction.getTransactionRef());

        return TopUpEvents.TopUpInitiated.newBuilder()
            .setEventId(java.util.UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setUserId(transaction.getSenderUserId().toString())
            .setWalletId(transaction.getSenderWalletId().toString())
            .setVaNumber(virtualAccount.getVaNumber())
            .setProvider(virtualAccount.getProvider().name())
            .setAmount(transaction.getAmount().doubleValue())
            .setFee(transaction.getFee().doubleValue())
            .setTotalAmount(transaction.getTotalAmount().doubleValue())
            .setCurrency(transaction.getCurrency())
            .setExpiresAt(toTimestamp(virtualAccount.getExpiresAt().toInstant()))
            .setCreatedAt(toTimestamp(transaction.getCreatedAt().toInstant()))
            .setTimestamp(toTimestamp(Instant.now()))
            .build();
    }

    public TopUpEvents.TopUpCompleted createTopUpCompletedEvent(
        Transaction transaction,
        VirtualAccount virtualAccount
    ) {
        log.debug("Creating TOP_UP_COMPLETED event for transaction: {}", transaction.getTransactionRef());

        return TopUpEvents.TopUpCompleted.newBuilder()
            .setEventId(java.util.UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setUserId(transaction.getSenderUserId().toString())
            .setWalletId(transaction.getSenderWalletId().toString())
            .setVaNumber(virtualAccount.getVaNumber())
            .setProvider(virtualAccount.getProvider().name())
            .setAmount(transaction.getAmount().doubleValue())
            .setPaidAmount(virtualAccount.getPaidAmount().doubleValue())
            .setCurrency(transaction.getCurrency())
            .setPaidAt(toTimestamp(virtualAccount.getPaidAt().toInstant()))
            .setCompletedAt(toTimestamp(transaction.getCompletedAt().toInstant()))
            .setTimestamp(toTimestamp(Instant.now()))
            .build();
    }

    public TopUpEvents.TopUpFailed createTopUpFailedEvent(
        Transaction transaction,
        VirtualAccount virtualAccount,
        String failureReason
    ) {
        log.debug("Creating TOP_UP_FAILED event for transaction: {}", transaction.getTransactionRef());

        return TopUpEvents.TopUpFailed.newBuilder()
            .setEventId(java.util.UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setUserId(transaction.getSenderUserId().toString())
            .setVaNumber(virtualAccount.getVaNumber())
            .setProvider(virtualAccount.getProvider().name())
            .setAmount(transaction.getAmount().doubleValue())
            .setFailureReason(failureReason != null ? failureReason : "Unknown error")
            .setFailedAt(toTimestamp(Instant.now()))
            .setTimestamp(toTimestamp(Instant.now()))
            .build();
    }

    public TopUpEvents.TopUpExpired createTopUpExpiredEvent(
        Transaction transaction,
        VirtualAccount virtualAccount
    ) {
        log.debug("Creating TOP_UP_EXPIRED event for transaction: {}", transaction.getTransactionRef());

        return TopUpEvents.TopUpExpired.newBuilder()
            .setEventId(java.util.UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setUserId(transaction.getSenderUserId().toString())
            .setVaNumber(virtualAccount.getVaNumber())
            .setProvider(virtualAccount.getProvider().name())
            .setAmount(transaction.getAmount().doubleValue())
            .setExpiresAt(toTimestamp(virtualAccount.getExpiresAt().toInstant()))
            .setExpiredAt(toTimestamp(virtualAccount.getExpiredAt().toInstant()))
            .setTimestamp(toTimestamp(Instant.now()))
            .build();
    }

    public TopUpEvents.TopUpCancelled createTopUpCancelledEvent(
        Transaction transaction,
        VirtualAccount virtualAccount
    ) {
        log.debug("Creating TOP_UP_CANCELLED event for transaction: {}", transaction.getTransactionRef());

        return TopUpEvents.TopUpCancelled.newBuilder()
            .setEventId(java.util.UUID.randomUUID().toString())
            .setTransactionId(transaction.getId().toString())
            .setTransactionRef(transaction.getTransactionRef())
            .setUserId(transaction.getSenderUserId().toString())
            .setVaNumber(virtualAccount.getVaNumber())
            .setProvider(virtualAccount.getProvider().name())
            .setAmount(transaction.getAmount().doubleValue())
            .setCancelledAt(toTimestamp(virtualAccount.getCancelledAt().toInstant()))
            .setTimestamp(toTimestamp(Instant.now()))
            .build();
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }
}
