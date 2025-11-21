package com.bni.orange.notification.consumer;

import com.bni.orange.notification.model.PaymentIntentCreatedEvent;
import com.bni.orange.notification.model.PaymentStatusUpdatedEvent;
import com.bni.orange.notification.model.SplitBillCreatedEvent;
import com.bni.orange.notification.model.SplitBillRemindedEvent;
import com.bni.orange.notification.service.PaymentNotificationService;
import com.bni.orange.notification.service.SplitBillWhatsAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class SplitBillAndPaymentKafkaConsumers {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SplitBillWhatsAppService splitBillWhatsAppService;
  private final PaymentNotificationService paymentNotificationService;
  private final DlqProducer dlqProducer;

  @KafkaListener(
      topics = "${orange.kafka.topics.splitbill-created:splitbill.events.created}",
      groupId = "${orange.kafka.groups.split-bill}",
      concurrency = "2"
  )
  public void onSplitBillCreated(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
    try {
      var json = new String(record.value(), StandardCharsets.UTF_8);
      var event = MAPPER.readValue(json, SplitBillCreatedEvent.class);

      if (isBlank(event.getBillId()) || isBlank(event.getOwnerUserId())) {
        throw new IllegalArgumentException("billId/ownerUserId missing");
      }

      log.info("SplitBillCreated received: billId={}, owner={}, ownerShortLink={}",
          event.getBillId(), maskUser(event.getOwnerUserId()), maskUrl(event.getOwnerShortLink()));

      splitBillWhatsAppService.sendBillCreated(event).block(Duration.ofSeconds(35));
      ack.acknowledge();
      log.info("SplitBillCreated processed & acked: billId={}", event.getBillId());
    } catch (Exception e) {
      log.error("SplitBillCreated failed. key={}, offset={}. Send to DLQ.", record.key(), record.offset(), e);
      dlqProducer.send(record, e);
      ack.acknowledge();
    }
  }

  @KafkaListener(
      topics = "${orange.kafka.topics.splitbill-reminded:splitbill.events.reminded}",
      groupId = "${orange.kafka.groups.split-bill}",
      concurrency = "3"
  )
  public void onSplitBillReminded(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
    try {
      var json = new String(record.value(), StandardCharsets.UTF_8);
      var event = MAPPER.readValue(json, SplitBillRemindedEvent.class);

      if (isBlank(event.getBillId()) || isBlank(event.getRemindedByUserId())) {
        throw new IllegalArgumentException("billId/remindedByUserId missing");
      }

      log.info("SplitBillReminded received: billId={}, remindedBy={}, channels={}",
          event.getBillId(), maskUser(event.getRemindedByUserId()), event.getRequestedChannels());

      splitBillWhatsAppService.sendBillReminded(event).block(Duration.ofSeconds(35));
      ack.acknowledge();
      log.info("SplitBillReminded processed & acked: billId={}", event.getBillId());
    } catch (Exception e) {
      log.error("SplitBillReminded failed. key={}, offset={}. Send to DLQ.", record.key(), record.offset(), e);
      dlqProducer.send(record, e);
      ack.acknowledge();
    }
  }

  @KafkaListener(
      topics = "${orange.kafka.topics.payment-intent:payment.intent.created}",
      groupId = "${orange.kafka.groups.payment}",
      concurrency = "3"
  )
  public void onPaymentIntentCreated(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
    try {
      var json = new String(record.value(), StandardCharsets.UTF_8);
      var event = MAPPER.readValue(json, PaymentIntentCreatedEvent.class);

      if (isBlank(event.getPaymentIntentId()) || isBlank(event.getUserId())) {
        throw new IllegalArgumentException("paymentIntentId/userId missing");
      }

      log.info("PaymentIntentCreated received: id={}, user={}",
          event.getPaymentIntentId(), maskUser(event.getUserId()));

      paymentNotificationService.sendPaymentIntent(event).block(Duration.ofSeconds(35));
      ack.acknowledge();
      log.info("PaymentIntentCreated processed & acked: id={}", event.getPaymentIntentId());
    } catch (Exception e) {
      log.error("PaymentIntentCreated failed. key={}, offset={}. Send to DLQ.", record.key(), record.offset(), e);
      dlqProducer.send(record, e);
      ack.acknowledge();
    }
  }

  @KafkaListener(
      topics = "${orange.kafka.topics.payment-status:payment.status.updated}",
      groupId = "${orange.kafka.groups.payment}",
      concurrency = "3"
  )
  public void onPaymentStatusUpdated(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
    try {
      var json = new String(record.value(), StandardCharsets.UTF_8);
      var event = MAPPER.readValue(json, PaymentStatusUpdatedEvent.class);

      if (isBlank(event.getPaymentIntentId()) || isBlank(event.getStatus())) {
        throw new IllegalArgumentException("paymentIntentId/status missing");
      }

      log.info("PaymentStatusUpdated received: id={}, status={}",
          event.getPaymentIntentId(), event.getStatus());

      paymentNotificationService.sendPaymentStatus(event).block(Duration.ofSeconds(35));
      ack.acknowledge();
      log.info("PaymentStatusUpdated processed & acked: id={}", event.getPaymentIntentId());
    } catch (Exception e) {
      log.error("PaymentStatusUpdated failed. key={}, offset={}. Send to DLQ.", record.key(), record.offset(), e);
      dlqProducer.send(record, e);
      ack.acknowledge();
    }
  }

  // Helpers
  private boolean isBlank(String s) { return s == null || s.isBlank(); }
  private String maskUser(String id) { return (id == null || id.length() < 6) ? "***" : id.substring(0,3)+"***"+id.substring(id.length()-2); }
  private String maskUrl(String url) {
    if (url == null || url.isBlank()) return "***";
    int i = url.indexOf("://"); String scheme = i>0 ? url.substring(0,i+3) : "";
    String rest = i>0 ? url.substring(i+3) : url;
    if (rest.length() <= 6) return scheme + "***";
    return scheme + rest.substring(0,3) + "****" + rest.substring(rest.length()-2);
  }
}
