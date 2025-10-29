package com.bni.orange.transaction.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "orange.kafka.topics")
public record KafkaTopicProperties(
    Map<String, TopicDefinition> definitions
) {
    public record TopicDefinition(
        String name,
        Integer partitions,
        Integer replicas,
        Boolean compact
    ) {
    }
}