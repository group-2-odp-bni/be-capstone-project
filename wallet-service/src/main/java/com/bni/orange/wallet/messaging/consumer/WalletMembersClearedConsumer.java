package com.bni.orange.wallet.messaging.consumer;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.proto.EventEnvelope;
import com.bni.orange.wallet.proto.WalletMembersClearedEvent;
import com.bni.orange.wallet.service.command.projector.WalletReadModelProjector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Lazy(false)
@RequiredArgsConstructor
public class WalletMembersClearedConsumer {

    private final WalletReadModelProjector readModelProjector;

    @KafkaListener(
        topics = "${orange.kafka.topics.wallet-members-cleared:wallet.events.members-cleared}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, byte[]> record) {
        try {
            var env = EventEnvelope.parseFrom(record.value());
            if (!"WalletMembersCleared".equals(env.getEventType())) {
                return;
            }

            var payload = env.getPayload().unpack(WalletMembersClearedEvent.class);

            UUID walletId = UUID.fromString(payload.getWalletId());

            log.info("WalletMembersCleared consumed: key={} walletId={}",
                    record.key(), payload.getWalletId());

            var event = DomainEvents.WalletMembersCleared.builder()
                    .walletId(walletId)
                    .build();

            readModelProjector.projectWalletMembersCleared(event);

            log.info("Successfully projected WalletMembersCleared for walletId={}", walletId);
        } catch (Exception ex) {
            log.error("Failed to process WalletMembersCleared for key " + record.key(), ex);
        }
    }
}
