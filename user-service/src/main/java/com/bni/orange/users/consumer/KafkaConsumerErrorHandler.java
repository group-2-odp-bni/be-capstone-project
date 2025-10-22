package com.bni.orange.users.consumer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Global error handler for Kafka consumers.
 * Handles errors that occur during message processing and implements retry/DLQ logic.
 */
@Slf4j
@Component
public class KafkaConsumerErrorHandler implements CommonErrorHandler {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Override
    public boolean handleOne(
        @NonNull Exception thrownException,
        ConsumerRecord<?, ?> record,
        @NonNull Consumer<?, ?> consumer,
        @NonNull MessageListenerContainer container
    ) {
        log.error("Error processing Kafka message: topic={}, partition={}, offset={}, key={}",
            record.topic(),
            record.partition(),
            record.offset(),
            record.key(),
            thrownException
        );

        if (isRetryableError(thrownException)) {
            int attemptCount = getRetryAttemptCount(record);

            if (attemptCount < MAX_RETRY_ATTEMPTS) {
                log.warn("Retrying message processing (attempt {}/{}): topic={}, partition={}, offset={}",
                    attemptCount + 1, MAX_RETRY_ATTEMPTS, record.topic(), record.partition(), record.offset());

                return false;
            } else {
                log.error("Max retry attempts reached for message: topic={}, partition={}, offset={}. " +
                        "Message should be moved to DLQ.",
                    record.topic(), record.partition(), record.offset());
            }
        }

        return true;
    }

    private boolean isRetryableError(Throwable exception) {
        if (exception == null) {
            return false;
        }

        String exceptionName = exception.getClass().getSimpleName();

        if (exceptionName.contains("InvalidProtocolBuffer") ||
            exceptionName.contains("IllegalArgument") ||
            exceptionName.contains("JsonProcessing") ||
            exceptionName.contains("ValidationException")) {
            return false;
        }

        if (exceptionName.contains("DataAccessException") ||
            exceptionName.contains("TransientDataAccessException") ||
            exceptionName.contains("CannotAcquireLockException") ||
            exceptionName.contains("DeadlockLoserDataAccessException") ||
            exceptionName.contains("Timeout") ||
            exceptionName.contains("ConnectionException")) {
            return true;
        }

        if (exception.getCause() != null && exception.getCause() != exception) {
            return isRetryableError(exception.getCause());
        }

        return true;
    }

    private int getRetryAttemptCount(ConsumerRecord<?, ?> record) {
        // Simple implementation: return 0
        // In production, you would:
        // 1. Read retry count from record headers
        // 2. Increment and store back to headers
        // 3. Or use a retry topic with attempt tracking
        return 0;
    }
}
