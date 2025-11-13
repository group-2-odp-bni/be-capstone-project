package com.bni.orange.authentication.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google.recaptcha")
public record CaptchaProperties(
    boolean enabled,
    @NotBlank String secret,
    @NotBlank String url,
    String hostname,
    double scoreThreshold
) {}
