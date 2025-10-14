package com.bni.orange.users.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "orange.kafka.topics")
public record KafkaTopicProperties(
    @Valid
    @NotNull
    Map<String, TopicConfig> definitions
) {
    public record TopicConfig(
        @NotBlank
        String name,

        @Min(1)
        @DefaultValue("3")
        Integer partitions,

        @Min(1)
        @DefaultValue("1")
        Integer replicas,

        @DefaultValue("false")
        Boolean compact
    ) {
    }
}
