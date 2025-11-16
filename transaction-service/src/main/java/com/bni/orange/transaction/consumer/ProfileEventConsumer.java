package com.bni.orange.transaction.consumer;

import com.bni.orange.transaction.service.QuickTransferService;
import com.bni.orange.users.proto.UserProfileNameUpdatedEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileEventConsumer {

    private final QuickTransferService quickTransferService;

    @KafkaListener(
        topics = "user.profile.name-updated",
        groupId = "transaction-service-quick-transfer-sync",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleNameUpdated(
        @Payload byte[] message,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        try {
            var event = UserProfileNameUpdatedEvent.parseFrom(message);
            var userId = UUID.fromString(event.getUserId());

            log.info("Received profile name updated event for user: {} (partition: {}, offset: {}, newName: '{}')",
                userId, partition, offset, event.getName());

            quickTransferService.syncRecipientName(userId, event.getName());

            acknowledgment.acknowledge();
            log.debug("Successfully processed profile name update event for user: {}", userId);

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse profile name updated event (partition: {}, offset: {}). Skipping and acknowledging to prevent reprocessing.",
                partition, offset, e);
            acknowledgment.acknowledge();

        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID format in event (partition: {}, offset: {}). Skipping and acknowledging.",
                partition, offset, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing profile name updated event (partition: {}, offset: {}). Message will be retried.", partition, offset, e);
            // Do NOT acknowledge - let Kafka retry
        }
    }
}
