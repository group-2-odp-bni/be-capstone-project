package com.bni.orange.authentication.service.captcha;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "google.recaptcha")
public record CaptchaProperties(
    @NotBlank String secret,
    @NotBlank String url
) {}
