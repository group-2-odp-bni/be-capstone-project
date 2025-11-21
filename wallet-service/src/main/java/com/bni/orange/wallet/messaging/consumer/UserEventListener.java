package com.bni.orange.wallet.messaging.consumer;

import com.bni.orange.authentication.proto.UserRegisteredEvent;
import com.bni.orange.wallet.model.enums.WalletType;
import com.bni.orange.wallet.model.request.wallet.WalletCreateRequest;
import com.bni.orange.wallet.service.command.WalletCommandService;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Lazy(false)
@RequiredArgsConstructor
public class UserEventListener {

    private final WalletCommandService walletCommandService;

    @KafkaListener(topics = "auth.user.registered", groupId = "wallet-service-group")
    public void handleUserRegistered(byte[] data) {
        log.info("Received user-registered event from Kafka");
        try {
            var event = UserRegisteredEvent.parseFrom(data);
            var userIdStr = event.getUserId();

            if (userIdStr == null || userIdStr.isEmpty()) {
                log.error("CRITICAL: User ID is missing from user-registered event. Event will be skipped.");
                return;
            }

            UUID userId;
            try {
                userId = UUID.fromString(userIdStr);
            } catch (IllegalArgumentException e) {
                log.error("CRITICAL: Invalid UUID format for user ID: {}. Event will be skipped.", userIdStr);
                return;
            }

            log.info("Processing user-registered event for userId={}", userId);

            var createRequest = WalletCreateRequest.builder()
                .type(WalletType.PERSONAL)
                .name("MAIN")
                .metadata(Map.of())
                .setAsDefaultReceive(true)
                .build();

            var idempotencyKey = "user-registered:" + userId;

            log.info("Creating default PERSONAL wallet for userId={} with idempotencyKey={}", userId, idempotencyKey);

            var result = walletCommandService.createWalletForUser(userId, createRequest, idempotencyKey);

            log.info("Successfully created default wallet for userId={}, walletId={}, walletName={}",
                userId, result.getId(), result.getName());

        } catch (InvalidProtocolBufferException e) {
            log.error("CRITICAL: Failed to parse Protobuf message from topic auth.user.registered. " +
                "Message will be skipped. Error: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("CRITICAL: Unexpected error while processing user-registered event. " +
                "userId may not have default wallet! Error: {}", e.getMessage(), e);
        }
    }
}
