package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final RedisPrefixProperties redisProperties;

    public void blacklistToken(String jti, Duration validityDuration) {
        var key = redisProperties.prefix().jwtBlacklist() + jti;
        redisTemplate.opsForValue().set(key, "blacklisted", validityDuration);
    }

    public boolean isTokenBlacklisted(String jti) {
        var key = redisProperties.prefix().jwtBlacklist() + jti;
        return redisTemplate.hasKey(key);
    }
}