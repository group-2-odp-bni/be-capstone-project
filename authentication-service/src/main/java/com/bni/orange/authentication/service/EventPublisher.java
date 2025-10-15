package com.bni.orange.authentication.service;

import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Executor kafkaVirtualThreadExecutor;

    public <T extends Message> void publish(String topic, String key, T event) {
        CompletableFuture
            .runAsync(
                () -> publishSync(topic, key, event), kafkaVirtualThreadExecutor
            )
            .exceptionally(throwable -> {
                log.error("Async publish failed for topic: {}, key: {}, error: {}",
                    topic, key, throwable.getMessage(), throwable);
                return null;
            });
    }

    public <T extends Message> CompletableFuture<SendResult<String, byte[]>> publishAsync(String topic, String key, T event) {
        return CompletableFuture.supplyAsync(
            () -> publishSync(topic, key, event),
            kafkaVirtualThreadExecutor
        );
    }

    public <T extends Message> void publishWithCallback(
        String topic, String key,
        T event,
        Consumer<SendResult<String, byte[]>> onSuccess,
        Consumer<Throwable> onError
    ) {
        publishAsync(topic, key, event)
            .thenAccept(onSuccess)
            .exceptionally(throwable -> {
                onError.accept(throwable);
                return null;
            });
    }

    private <T extends Message> SendResult<String, byte[]> publishSync(String topic, String key, T event) {
        try {
            log.debug("Publishing event to topic: {}, key: {}, eventType: {}",
                topic, key, event.getClass().getSimpleName());

            var future = kafkaTemplate.send(topic, key, event.toByteArray());
            var result = future.get();

            log.info("Event published successfully. Topic: {}, Partition: {}, Offset: {}, Key: {}",
                topic,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                key
            );

            return result;
        } catch (Exception e) {
            log.error("Failed to publish event to topic: {}, key: {}, eventType: {}, error: {}",
                topic, key, event.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Event publication failed for topic: " + topic, e);
        }
    }

    public <T extends Message> void publish(String topic, T event) {
        publish(topic, null, event);
    }

    public <T extends Message> CompletableFuture<Void> publishBatch(
        String topic,
        java.util.Map<String, T> events
    ) {
        var futures = events.entrySet().stream()
            .map(entry -> publishAsync(topic, entry.getKey(), entry.getValue()))
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    public boolean isKafkaAvailable() {
        try {
            kafkaTemplate.getProducerFactory().createProducer().close();
            return true;
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return false;
        }
    }
}