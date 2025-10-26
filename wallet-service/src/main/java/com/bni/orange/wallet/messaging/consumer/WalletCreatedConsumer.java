package com.bni.orange.wallet.messaging.consumer;

import com.bni.orange.wallet.proto.EventEnvelope;
import com.bni.orange.wallet.proto.WalletCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WalletCreatedConsumer {

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

      // TODO: update read-model di sini
    } catch (Exception ex) {
      log.error("Failed to process WalletCreated message", ex);
      // TODO: DLQ / retry policy
    }
  }
}
