package com.bni.orange.authentication.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "orange.kafka.producer")
public record KafkaProducerProperties(
    @NotBlank
    String bootstrapServers,
    @NotBlank
    @DefaultValue("org.apache.kafka.common.serialization.StringSerializer")
    String keySerializer,
    @NotBlank
    @DefaultValue("org.apache.kafka.common.serialization.ByteArraySerializer")
    String valueSerializer,
    @NotBlank
    @DefaultValue("all")
    String acks,
    int retries,
    @NotNull
    Duration backoff,
    boolean idempotence,
    @NotBlank
    String compressionType,
    @NotNull
    Reliability reliability,
    @NotNull
    Batching batching
) {
    public record Reliability(
        int maxInFlightRequestsPerConnection,
        @NotNull
        Duration requestTimeout,
        @NotNull
        Duration deliveryTimeout
    ) {
    }

    public record Batching(
        long lingerMs,
        int batchSize
    ) {
    }
}