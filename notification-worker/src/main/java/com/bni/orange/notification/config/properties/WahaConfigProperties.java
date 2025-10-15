package com.bni.orange.notification.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "orange.waha-client")
public record WahaConfigProperties(
    @NotBlank String baseUrl,
    @NotBlank String apiKey,
    @NotBlank String sessionName,
    @NotNull Duration timeout,
    @NotNull RetryConfig retry
) {
    public record RetryConfig(
        int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff,
        double multiplier
    ) {}
}