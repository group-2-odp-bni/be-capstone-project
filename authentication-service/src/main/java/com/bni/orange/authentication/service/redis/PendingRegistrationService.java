package com.bni.orange.authentication.service.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PendingRegistrationService {

    private static final String KEY_PREFIX = "registration:pending:";
    private static final Duration REGISTRATION_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;

    private String getKey(String phoneNumber) {
        return KEY_PREFIX + phoneNumber;
    }

    public void save(String phoneNumber) {
        redisTemplate.opsForValue().set(getKey(phoneNumber), "pending", REGISTRATION_TTL);
    }

    public boolean exists(String phoneNumber) {
        return redisTemplate.hasKey(getKey(phoneNumber));
    }

    public void delete(String phoneNumber) {
        redisTemplate.delete(getKey(phoneNumber));
    }
}
