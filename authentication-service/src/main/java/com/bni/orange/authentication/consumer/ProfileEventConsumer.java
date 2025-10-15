package com.bni.orange.authentication.consumer;

import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.users.proto.UserProfileEmailVerifiedEvent;
import com.bni.orange.users.proto.UserProfileNameUpdatedEvent;
import com.bni.orange.users.proto.UserProfilePhoneVerifiedEvent;
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

    private final UserRepository userRepository;

    @KafkaListener(
        topics = "user.profile.email-verified",
        groupId = "auth-service-profile-sync",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleEmailVerified(
        @Payload byte[] message,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        try {
            var event = UserProfileEmailVerifiedEvent.parseFrom(message);
            var userId = UUID.fromString(event.getUserId());

            log.info("Received email verified event for user: {} (partition: {}, offset: {})",
                userId, partition, offset);

            userRepository.findById(userId).ifPresentOrElse(
                user -> {
                    user.setEmail(event.getEmail());
                    user.setEmailVerified(true);
                    userRepository.save(user);
                    log.info("Email updated for user: {} -> {}", userId, event.getEmail());
                },
                () -> log.warn("User not found for email update: {}", userId)
            );

            acknowledgment.acknowledge();
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse email verified event", e);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing email verified event", e);
        }
    }

    @KafkaListener(
        topics = "user.profile.phone-verified",
        groupId = "auth-service-profile-sync",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePhoneVerified(
        @Payload byte[] message,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        try {
            var event = UserProfilePhoneVerifiedEvent.parseFrom(message);
            var userId = UUID.fromString(event.getUserId());

            log.info("Received phone verified event for user: {} (partition: {}, offset: {})",
                userId, partition, offset);

            userRepository.findById(userId).ifPresentOrElse(
                user -> {
                    user.setPhoneNumber(event.getPhoneNumber());
                    user.setPhoneVerified(true);
                    userRepository.save(user);
                    log.info("Phone updated for user: {} -> {}", userId, event.getPhoneNumber());
                },
                () -> log.warn("User not found for phone update: {}", userId)
            );

            acknowledgment.acknowledge();
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse phone verified event", e);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing phone verified event", e);
        }
    }

    @KafkaListener(
        topics = "user.profile.name-updated",
        groupId = "auth-service-profile-sync",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleNameUpdated(
        @Payload byte[] message,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        try {
            var event = UserProfileNameUpdatedEvent.parseFrom(message);
            var userId = UUID.fromString(event.getUserId());

            log.info("Received name updated event for user: {} (partition: {}, offset: {})", userId, partition, offset);

            userRepository.findById(userId).ifPresentOrElse(
                user -> {
                    user.setName(event.getName());
                    userRepository.save(user);
                    log.info("Name updated for user: {} -> {}", userId, event.getName());
                },
                () -> log.warn("User not found for name update: {}", userId)
            );

            acknowledgment.acknowledge();
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse name updated event", e);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing name updated event", e);
        }
    }
}
