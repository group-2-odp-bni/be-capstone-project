package com.bni.orange.transaction.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orange.kafka.topics.topup")
public record TopUpEventTopicProperties(
    String topUpInitiated,
    String topUpCompleted,
    String topUpFailed,
    String topUpExpired,
    String topUpCancelled
) {
}
