package com.bni.orange.users.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "spring.kafka.producer")
public record KafkaProducerProperties(
    @NotBlank
    @DefaultValue("localhost:9092")
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

    @DefaultValue("3")
    int retries,

    @NotNull
    @DefaultValue("1s")
    Duration retryBackoffMs,

    @NotBlank
    @DefaultValue("snappy")
    String compressionType,

    @DefaultValue("true")
    boolean enableIdempotence,

    @NotNull
    Reliability reliability,

    @NotNull
    Batching batching
) {
    public record Reliability(
        @DefaultValue("5")
        int maxInFlightRequestsPerConnection,

        @NotNull
        @DefaultValue("30s")
        Duration requestTimeout,

        @NotNull
        @DefaultValue("2m")
        Duration deliveryTimeout
    ) {
    }

    public record Batching(
        @DefaultValue("20")
        int lingerMs,

        @DefaultValue("32768")
        int batchSize
    ) {
    }
}
