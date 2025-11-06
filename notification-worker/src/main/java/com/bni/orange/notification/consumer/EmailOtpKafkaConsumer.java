package com.bni.orange.notification.consumer;

import com.bni.orange.users.proto.OtpEmailNotificationEvent;
import com.bni.orange.notification.service.EmailService;
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
public class EmailOtpKafkaConsumer {

    private final EmailService emailService;
    private final DlqProducer dlqProducer;

    @KafkaListener(
        topics = "${orange.kafka.topics.otp-email}",
        groupId = "notification-otp-email-group",
        concurrency = "3"
    )
    public void listen(ConsumerRecord<String, byte[]> record, Acknowledgment acknowledgment) {
        OtpEmailNotificationEvent event = null;

        try {
            event = OtpEmailNotificationEvent.parseFrom(record.value());
            log.info("Received email OTP notification - Topic: {}, Partition: {}, Offset: {}, User ID: {}, Email: {}",
                record.topic(), record.partition(), record.offset(), event.getUserId(), maskEmail(event.getEmail()));

            emailService.sendOtp(event).block();

            acknowledgment.acknowledge();
            log.info("Successfully sent OTP email and acknowledged - User ID: {}, Offset: {}",
                event.getUserId(), record.offset());

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to deserialize Protobuf message - Key: {}, Partition: {}, Offset: {}. Sending to DLQ.",
                record.key(), record.partition(), record.offset(), e);
            dlqProducer.send(record, e);
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process email OTP notification after all retries - User ID: {}, Key: {}, Offset: {}. Sending to DLQ.",
                event != null ? event.getUserId() : "unknown", record.key(), record.offset(), e);
            dlqProducer.send(record, e);
            acknowledgment.acknowledge();
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return "***@" + domain;
        }

        int visibleChars = Math.min(2, localPart.length() / 3);
        String visible = localPart.substring(0, visibleChars);
        return visible + "***@" + domain;
    }
}
