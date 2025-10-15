package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int DELAY_BASE = 2;
    private static final int MAX_DELAY_SECONDS = 8;
    private static final int ATTEMPTS_BEFORE_DELAY = 2;
    private final StringRedisTemplate redisTemplate;
    private final RedisPrefixProperties redisProperties;

    public void loginFailed(String key) {
        var failKey = redisProperties.prefix().pinFailCount() + key;
        var ops = redisTemplate.opsForValue();
        var attempts = ops.increment(failKey);

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            ops.set(redisProperties.prefix().pinLocked() + key, "true", Duration.ofMinutes(15));
            redisTemplate.delete(failKey);
        } else {
            redisTemplate.expire(failKey, Duration.ofMinutes(15));
        }
    }

    public void loginSucceeded(String key) {
        redisTemplate.delete(redisProperties.prefix().pinFailCount() + key);
        redisTemplate.delete(redisProperties.prefix().pinLocked() + key);
    }

    public boolean isLocked(String key) {
        return redisTemplate.hasKey(redisProperties.prefix().pinLocked() + key);
    }

    public int getAttemptsLeft(String key) {
        var attempts = Optional.ofNullable(redisTemplate.opsForValue().get(redisProperties.prefix().pinFailCount() + key))
            .map(Long::parseLong)
            .orElse(0L);
        return (int) Math.max(0, MAX_ATTEMPTS - attempts);
    }


    public void applyProgressiveDelay(String key) {
        var failKey = redisProperties.prefix().pinFailCount() + key;
        long attempts = Optional.ofNullable(redisTemplate.opsForValue().get(failKey))
            .map(Long::parseLong)
            .orElse(0L);

        if (attempts >= ATTEMPTS_BEFORE_DELAY) {
            long delaySeconds = Math.min((long) Math.pow(DELAY_BASE, attempts - 1), MAX_DELAY_SECONDS);

            try {
                log.debug("Applying progressive delay of {} seconds for key: {}", delaySeconds, key);
                Thread.sleep(delaySeconds * 1000);
            } catch (InterruptedException e) {
                log.warn("Progressive delay interrupted for key: {}", key);
                Thread.currentThread().interrupt();
            }
        }
    }
}