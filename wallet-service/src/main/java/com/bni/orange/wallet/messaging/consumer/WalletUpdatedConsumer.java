package com.bni.orange.wallet.messaging.consumer;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import com.bni.orange.wallet.proto.EventEnvelope;
import com.bni.orange.wallet.proto.WalletUpdatedEvent;
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
public class WalletUpdatedConsumer {

    private final WalletReadModelProjector readModelProjector;

    @KafkaListener(
            topics = "${orange.kafka.topics.wallet-updated:wallet.events.updated}", 
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, byte[]> record) {
        try {
            var env = EventEnvelope.parseFrom(record.value());
            if (!"WalletUpdated".equals(env.getEventType())) return; 

            var payload = env.getPayload().unpack(WalletUpdatedEvent.class); 

            log.info("WalletUpdated consumed: key={} walletId={} userId={}",
                    record.key(), payload.getWalletId(), payload.getUserId());

            var event = DomainEvents.WalletUpdated.builder()
                    .walletId(UUID.fromString(payload.getWalletId()))
                    .userId(UUID.fromString(payload.getUserId()))
                    .type(WalletType.valueOf(payload.getType()))
                    .status(WalletStatus.valueOf(payload.getStatus()))
                    .currency(payload.getCurrency())
                    .name(payload.getName())
                    .balanceSnapshot(new BigDecimal(payload.getBalanceSnapshot()))
                    .updatedAt(OffsetDateTime.parse(payload.getUpdatedAt()))
                    .build();

            readModelProjector.projectWalletUpdateFromEvent(event); 

            log.info("Successfully projected (update) read-model for walletId={}", payload.getWalletId());

        } catch (Exception ex) {
            log.error("Failed to process WalletUpdated message for key " + record.key(), ex);
        }
    }
}