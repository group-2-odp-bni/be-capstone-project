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
        log.info("Received raw message on topic auth.user.registered");
        try {
            var event = UserRegisteredEvent.parseFrom(data);
            log.info("Successfully parsed UserRegisteredEvent for user ID: {}", event.getUserId());

            var userIdStr = event.getUserId();
            if (userIdStr.isEmpty()) {
                log.error("User ID is missing from the user-registered event.");
                return;
            }

            var userId = UUID.fromString(userIdStr);

            var createRequest = WalletCreateRequest.builder()
                .type(WalletType.PERSONAL)
                .name("MAIN")
                .metadata(Map.of())
                .setAsDefaultReceive(true)
                .build();


            var idempotencyKey = "user-registered:" + userId;

            log.info("Creating default wallet for user ID: {}", userId);
            walletCommandService.createWalletForUser(userId, createRequest, idempotencyKey);
            log.info("Successfully created default wallet for user ID: {}", userId);
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse Protobuf message from topic auth.user.registered", e);
        } catch (Exception e) {
            log.error("An unexpected error occurred while processing user-registered event: {}", e.getMessage(), e);
        }
    }
}
