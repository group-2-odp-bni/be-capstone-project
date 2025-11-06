package com.bni.orange.notification.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "orange.email")
public record EmailConfigProperties(
    @NotBlank String fromAddress,
    @NotBlank String fromName,
    @NotNull RetryConfig retry
) {
    public record RetryConfig(
        int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff,
        double multiplier
    ) {}
}
