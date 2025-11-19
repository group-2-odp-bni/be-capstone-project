package com.bni.orange.notification.consumer;

import com.bni.orange.notification.service.InviteWhatsAppService;
import com.bni.orange.wallet.proto.EventEnvelope;
import com.bni.orange.wallet.proto.WalletInviteLinkGeneratedEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InviteLinkKafkaConsumer {

  private final InviteWhatsAppService whatsAppService;
  private final DlqProducer dlqProducer;

  @KafkaListener(
      topics = "${orange.kafka.topics.wallet-invite-generated:wallet.events.invite-generated}",
      groupId = "${orange.kafka.groups.invite-link}",
      concurrency = "3"
  )
  public void listen(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
    WalletInviteLinkGeneratedEvent event;

    try {
      var env = EventEnvelope.parseFrom(record.value());
      if (!"WalletInviteLinkGenerated".equals(env.getEventType())) {
        log.warn("Skip event type: {}", env.getEventType());
        ack.acknowledge();
        return;
      }

      event = env.getPayload().unpack(WalletInviteLinkGeneratedEvent.class);
      var phone = event.getPhoneE164();

      if (phone.isBlank()) {
        throw new IllegalArgumentException("Phone E164 missing in event");
      }

      log.info("InviteLinkGenerated received: walletId={}, phone={}",
          event.getWalletId(), mask(phone));

      // Kirim WA: link + kode (masked / plain sesuai kebijakan)
      whatsAppService
          .sendInviteLink(event)
          .block(java.time.Duration.ofSeconds(35));

      ack.acknowledge();
      log.info("Invite link sent & acked for phone={}", mask(phone));

    } catch (InvalidProtocolBufferException e) {
      log.error("Protobuf deserialization failed. key={}, offset={}. Send to DLQ.",
          record.key(), record.offset(), e);
      dlqProducer.send(record, e);
      ack.acknowledge();

    } catch (Exception e) {
      log.error("Processing failed. key={}, offset={}. Send to DLQ.",
          record.key(), record.offset(), e);
      dlqProducer.send(record, e);
      ack.acknowledge();
    }
  }

  private String mask(String e164) {
    if (e164 == null || e164.length() < 8) return "***";
    return e164.substring(0, 6) + "****" + e164.substring(e164.length()-2);
  }
}
