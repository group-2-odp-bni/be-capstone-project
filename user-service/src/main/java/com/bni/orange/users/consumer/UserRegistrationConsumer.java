package com.bni.orange.users.consumer;

import com.bni.orange.users.model.entity.UserProfile;
import com.bni.orange.users.model.enums.SyncStatus;
import com.bni.orange.users.proto.UserRegisteredEvent;
import com.bni.orange.users.repository.UserProfileRepository;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrationConsumer {

    private final UserProfileRepository profileRepository;

    @KafkaListener(
        topics = "${orange.kafka.topics.user-registered:auth.user.registered}",
        groupId = "${spring.kafka.consumer.group-id:user-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUserRegistered(
        @Payload byte[] payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment
    ) {
        try {
            log.info("Received UserRegisteredEvent: key={}, partition={}, offset={}", key, partition, offset);

            var event = UserRegisteredEvent.parseFrom(payload);
            var userId = UUID.fromString(event.getUserId());

            if (profileRepository.existsById(userId)) {
                log.warn("User profile already exists for userId={}, skipping creation (idempotent)", userId);
                acknowledgment.acknowledge();
                return;
            }

            var profile = UserProfile.builder()
                .id(userId)
                .syncStatus(SyncStatus.PENDING_SYNC)
                .build();

            var email = !event.getEmail().trim().isEmpty()
                ? event.getEmail()
                : null;
            var profileImageUrl = !event.getProfileImageUrl().trim().isEmpty()
                ? event.getProfileImageUrl()
                : null;

            profile.syncFromAuthService(
                event.getName(),
                event.getPhoneNumber(),
                email,
                profileImageUrl,
                event.getPhoneVerified(),
                event.getEmailVerified()
            );

            profileRepository.save(profile);

            log.info("User profile synced from auth-service: userId={}, phoneNumber={}, email={}, phoneVerified={}, emailVerified={}",
                userId, event.getPhoneNumber(), event.getEmail(), event.getPhoneVerified(), event.getEmailVerified());

            acknowledgment.acknowledge();

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse UserRegisteredEvent protobuf: partition={}, offset={}", partition, offset, e);
            throw new RuntimeException("Invalid protobuf message", e);

        } catch (IllegalArgumentException e) {
            log.error("Invalid userId format in event: partition={}, offset={}", partition, offset, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Unexpected error processing UserRegisteredEvent: partition={}, offset={}",
                partition, offset, e);
            throw new RuntimeException("Failed to process user registration event", e);
        }
    }

    private OffsetDateTime convertToOffsetDateTime(long epochSeconds) {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(epochSeconds),
            ZoneId.systemDefault()
        );
    }
}
