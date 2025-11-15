package com.bni.orange.notification.consumer;

import com.bni.orange.authentication.proto.OtpNotificationEvent;
import com.bni.orange.notification.service.WhatsAppService;
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
public class OtpKafkaConsumer {

    private final WhatsAppService whatsAppService;
    private final DlqProducer dlqProducer;

    @KafkaListener(
        topics = "${orange.kafka.topics.otp-whatsapp}",
        groupId = "${orange.kafka.groups.otp-whatsapp}",
        concurrency = "3"
    )
    public void listen(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        OtpNotificationEvent event = null;

        try {
            event = OtpNotificationEvent.parseFrom(record.value());
            log.info("Received OTP notification - Topic: {}, Partition: {}, Offset: {}, User ID: {}, Phone: {}",
                record.topic(), record.partition(), record.offset(), event.getUserId(), event.getPhoneNumber());

            whatsAppService.sendOtp(event).block();

            acknowledgment.acknowledge();
            log.info("Successfully sent OTP and acknowledged - User ID: {}, Offset: {}",
                event.getUserId(), record.offset());

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to deserialize Protobuf message - Key: {}, Partition: {}, Offset: {}. Sending to DLQ.",
                record.key(), record.partition(), record.offset(), e);
            dlqProducer.send(record, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process OTP notification after all retries - User ID: {}, Key: {}, Offset: {}. Sending to DLQ.",
                event != null ? event.getUserId() : "unknown", record.key(), record.offset(), e);
            dlqProducer.send(record, e);
            acknowledgment.acknowledge();
        }
    }
}