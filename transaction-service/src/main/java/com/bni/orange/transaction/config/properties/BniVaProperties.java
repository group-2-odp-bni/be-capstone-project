package com.bni.orange.transaction.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orange.integration.bni-va")
public record BniVaProperties(
    String baseUrl,
    String clientId,
    String clientSecret,
    Duration timeout,
    Boolean mockEnabled
) {
    public BniVaProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(30);
        }
        if (mockEnabled == null) {
            mockEnabled = false;
        }
    }
}
