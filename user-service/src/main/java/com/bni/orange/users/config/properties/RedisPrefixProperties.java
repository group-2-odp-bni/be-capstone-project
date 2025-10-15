package com.bni.orange.users.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orange.redis")
public record RedisPrefixProperties(
    Prefix prefix
) {
    public record Prefix(
        String otpEmail,
        String otpPhone,
        String otpEmailCooldown,
        String otpPhoneCooldown,
        String otpEmailFailCount,
        String otpPhoneFailCount,
        String otpEmailLocked,
        String otpPhoneLocked
    ) {
    }
}
