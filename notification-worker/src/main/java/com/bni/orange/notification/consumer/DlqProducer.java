package com.bni.orange.notification.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DlqProducer {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    @Value("${orange.kafka.topics.otp-whatsapp-dlq}")
    private String dlqTopic;

    public void send(ConsumerRecord<String, byte[]> originalRecord, Exception exception) {
        log.warn("Sending message to DLQ topic [{}]. Reason: {}", dlqTopic, exception.getMessage());
        var producerRecord = new ProducerRecord<>(dlqTopic, originalRecord.key(), originalRecord.value());
        originalRecord.headers().forEach(header -> producerRecord.headers().add(header));
        producerRecord.headers().add("X-DLQ-Reason", exception.getClass().getSimpleName().getBytes());
        producerRecord.headers().add("X-Original-Topic", originalRecord.topic().getBytes());
        
        kafkaTemplate.send(producerRecord);
    }
}