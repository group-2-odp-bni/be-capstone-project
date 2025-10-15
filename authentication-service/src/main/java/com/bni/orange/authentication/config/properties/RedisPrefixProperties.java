package com.bni.orange.authentication.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orange.redis")
public record RedisPrefixProperties(
    Prefix prefix
) {
    public record Prefix(
        String otp,
        String otpCooldown,
        String otpFailCount,
        String otpLocked,
        String pinFailCount,
        String pinLocked,
        String jwtBlacklist,
        String stateToken
    ) {
    }
}
