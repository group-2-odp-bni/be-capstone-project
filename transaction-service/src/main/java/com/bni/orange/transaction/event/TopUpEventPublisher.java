package com.bni.orange.transaction.event;

import com.bni.orange.transaction.config.properties.TopUpEventTopicProperties;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.entity.VirtualAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopUpEventPublisher {

    private final EventPublisher eventPublisher;
    private final TopUpEventFactory eventFactory;
    private final TopUpEventTopicProperties topicProperties;

    public void publishTopUpInitiated(Transaction transaction, VirtualAccount virtualAccount) {
        try {
            var event = eventFactory.createTopUpInitiatedEvent(transaction, virtualAccount);
            String key = transaction.getSenderUserId().toString();

            eventPublisher.publish(topicProperties.topUpInitiated(), key, event);

            log.info("Published TOP_UP_INITIATED event: transactionRef={}, vaNumber={}",
                transaction.getTransactionRef(), virtualAccount.getVaNumber());

        } catch (Exception e) {
            log.error("Failed to publish TOP_UP_INITIATED event for transaction: {}",
                transaction.getTransactionRef(), e);
        }
    }

    public void publishTopUpCompleted(Transaction transaction, VirtualAccount virtualAccount) {
        try {
            TopUpEvents.TopUpCompleted event = eventFactory.createTopUpCompletedEvent(transaction, virtualAccount);
            String key = transaction.getSenderUserId().toString();

            eventPublisher.publish(topicProperties.topUpCompleted(), key, event);

            log.info("Published TOP_UP_COMPLETED event: transactionRef={}, amount={}",
                transaction.getTransactionRef(), transaction.getAmount());

        } catch (Exception e) {
            log.error("Failed to publish TOP_UP_COMPLETED event for transaction: {}",
                transaction.getTransactionRef(), e);
        }
    }

    public void publishTopUpFailed(Transaction transaction, VirtualAccount virtualAccount, String failureReason) {
        try {
            TopUpEvents.TopUpFailed event = eventFactory.createTopUpFailedEvent(
                transaction, virtualAccount, failureReason);
            String key = transaction.getSenderUserId().toString();

            eventPublisher.publish(topicProperties.topUpFailed(), key, event);

            log.info("Published TOP_UP_FAILED event: transactionRef={}, reason={}",
                transaction.getTransactionRef(), failureReason);

        } catch (Exception e) {
            log.error("Failed to publish TOP_UP_FAILED event for transaction: {}",
                transaction.getTransactionRef(), e);
        }
    }

    public void publishTopUpExpired(Transaction transaction, VirtualAccount virtualAccount) {
        try {
            TopUpEvents.TopUpExpired event = eventFactory.createTopUpExpiredEvent(transaction, virtualAccount);
            String key = transaction.getSenderUserId().toString();

            eventPublisher.publish(topicProperties.topUpExpired(), key, event);

            log.info("Published TOP_UP_EXPIRED event: transactionRef={}, vaNumber={}",
                transaction.getTransactionRef(), virtualAccount.getVaNumber());

        } catch (Exception e) {
            log.error("Failed to publish TOP_UP_EXPIRED event for transaction: {}",
                transaction.getTransactionRef(), e);
        }
    }

    public void publishTopUpCancelled(Transaction transaction, VirtualAccount virtualAccount) {
        try {
            TopUpEvents.TopUpCancelled event = eventFactory.createTopUpCancelledEvent(transaction, virtualAccount);
            String key = transaction.getSenderUserId().toString();

            eventPublisher.publish(topicProperties.topUpCancelled(), key, event);

            log.info("Published TOP_UP_CANCELLED event: transactionRef={}", transaction.getTransactionRef());

        } catch (Exception e) {
            log.error("Failed to publish TOP_UP_CANCELLED event for transaction: {}",
                transaction.getTransactionRef(), e);
        }
    }
}
