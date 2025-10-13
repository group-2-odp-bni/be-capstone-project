package com.bni.orange.authentication.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orange.jwt")
public record JwtProperties(
    Duration accessTokenDuration,
    Duration refreshTokenDuration,
    Duration stateTokenDuration
) {
}