package com.bni.orange.wallet.messaging;

import com.bni.orange.wallet.proto.EventEnvelope;
import com.google.protobuf.Any;
import com.google.protobuf.util.Timestamps;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletEventPublisher {

  private final KafkaTemplate<String, byte[]> template;

  @Value("${orange.kafka.topics.wallet-created:wallet.events.created}")
  private String topicWalletCreated;

  @Value("${orange.kafka.topics.wallet-updated:wallet.events.updated}")
  private String topicWalletUpdated;

  @Value("${orange.kafka.topics.wallet-member-invited:wallet.events.member-invited}")
  private String topicMemberInvited;

  public void publish(String topic, String key, com.google.protobuf.Message payload,
                      String eventType, int version) {

  var envelope = EventEnvelope.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType(eventType)
        .setEventVersion(version)
        .setOccurredAt(Timestamps.fromMillis(Instant.now().toEpochMilli()))
        .setAggregateId(key)
        .setPayload(Any.pack(payload))
        .build();

    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(topic, null,null,
            key, envelope.toByteArray());

    record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
    record.headers().add("eventVersion", String.valueOf(version).getBytes(StandardCharsets.UTF_8));

    template.send(record);
  }

  public void publishWalletCreated(String walletId, com.google.protobuf.Message walletCreatedPayload) {
    publish(topicWalletCreated, walletId, walletCreatedPayload, "WalletCreated", 1);
  }
  public void publishWalletUpdated(String walletId, com.google.protobuf.Message walletUpdatedPayload) {
      publish(topicWalletUpdated, walletId, walletUpdatedPayload, "WalletUpdated", 1);
  }
  public void publishWalletMemberInvited(String key, com.google.protobuf.Message payload) {
      publish(topicMemberInvited, key, payload, "WalletMemberInvited", 1);
  }
}
