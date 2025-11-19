package com.bni.orange.notification.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "orange.user-service")
public record UserServiceConfigProperties(
    @NotBlank String baseUrl,
    @NotNull Duration timeout
) {}
