package com.bni.orange.notification.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orange.webhook")
public record WebhookConfigProperties(
    @NotBlank
    String hmacSecret
) {
}
