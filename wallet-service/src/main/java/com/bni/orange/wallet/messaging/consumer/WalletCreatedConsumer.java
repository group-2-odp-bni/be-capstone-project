package com.bni.orange.wallet.messaging.consumer;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import com.bni.orange.wallet.proto.EventEnvelope;
import com.bni.orange.wallet.proto.WalletCreatedEvent;
import com.bni.orange.wallet.service.command.projector.WalletReadModelProjector; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletCreatedConsumer {
    private final WalletReadModelProjector readModelProjector;
    @KafkaListener(
            topics = "${orange.kafka.topics.wallet-created:wallet.events.created}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, byte[]> record) {
        try {
            var env = EventEnvelope.parseFrom(record.value());
            if (!"WalletCreated".equals(env.getEventType())) return;
            var payload = env.getPayload().unpack(WalletCreatedEvent.class);

            log.info("WalletCreated consumed: key={} walletId={} userId={} ver={}",
                    record.key(), payload.getWalletId(), payload.getUserId(), env.getEventVersion());
            var event = DomainEvents.WalletCreated.builder()
                    .walletId(UUID.fromString(payload.getWalletId()))
                    .userId(UUID.fromString(payload.getUserId()))
                    .type(WalletType.valueOf(payload.getType())) 
                    .status(WalletStatus.valueOf(payload.getStatus()))
                    .currency(payload.getCurrency())
                    .name(payload.getName())
                    .balanceSnapshot(new BigDecimal(payload.getBalanceSnapshot()))
                    .defaultForUser(payload.getIsDefaultForUser())
                    .createdAt(OffsetDateTime.parse(payload.getCreatedAt()))
                    .updatedAt(OffsetDateTime.parse(payload.getUpdatedAt()))
                    .build();

            readModelProjector.projectNewWallet(event);

            log.info("Successfully projected read-model for walletId={}", payload.getWalletId());
            
        } catch (Exception ex) {
            log.error("Failed to process WalletCreated message for key " + record.key(), ex);
        }
    }
}