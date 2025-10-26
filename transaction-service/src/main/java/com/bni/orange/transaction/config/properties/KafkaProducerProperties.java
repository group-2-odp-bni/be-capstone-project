package com.bni.orange.transaction.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orange.kafka.producer")
public record KafkaProducerProperties(
    String bootstrapServers,
    String keySerializer,
    String valueSerializer,
    String acks,
    Integer retries,
    Duration backoff,
    String compressionType,
    Boolean idempotence,
    Reliability reliability,
    Batching batching
) {
    public record Reliability(
        Integer maxInFlightRequestsPerConnection,
        Duration requestTimeout,
        Duration deliveryTimeout
    ) {
    }

    public record Batching(
        Integer lingerMs,
        Integer batchSize
    ) {
    }
}
