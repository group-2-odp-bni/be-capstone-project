package com.bni.orange.authentication.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.kafka.consumer")
public record KafkaConsumerProperties(
    @NotBlank
    @DefaultValue("localhost:9092")
    String bootstrapServers,

    @NotBlank
    @DefaultValue("auth-service-profile-sync")
    String groupId,

    @NotBlank
    @DefaultValue("org.apache.kafka.common.serialization.StringDeserializer")
    String keyDeserializer,

    @NotBlank
    @DefaultValue("org.apache.kafka.common.serialization.ByteArrayDeserializer")
    String valueDeserializer,

    @NotBlank
    @DefaultValue("earliest")
    String autoOffsetReset,

    @DefaultValue("false")
    boolean enableAutoCommit,

    @DefaultValue("100")
    int maxPollRecords,

    @DefaultValue("300000")
    int maxPollIntervalMs,

    @DefaultValue("30000")
    int sessionTimeoutMs,

    @DefaultValue("10000")
    int heartbeatIntervalMs,

    @DefaultValue("3")
    int concurrency
) {
}
