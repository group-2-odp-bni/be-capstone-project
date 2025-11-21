package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlacklistServiceTest {

    @InjectMocks
    private BlacklistService blacklistService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisPrefixProperties redisProperties;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisPrefixProperties.Prefix prefix;

    private final String blacklistPrefix = "jwt_blacklist:";

    @BeforeEach
    void setUp() {
        when(redisProperties.prefix()).thenReturn(prefix);
        when(prefix.jwtBlacklist()).thenReturn(blacklistPrefix);
    }

    @Test
    @DisplayName("blacklistToken should construct correct key and call Redis set")
    void blacklistToken_shouldConstructKeyAndCallRedisSet() {
        var jti = "test-jti";
        var validityDuration = Duration.ofMinutes(15);
        var expectedKey = blacklistPrefix + jti;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        blacklistService.blacklistToken(jti, validityDuration);
        verify(valueOperations).set(expectedKey, "blacklisted", validityDuration);
    }

    @Test
    @DisplayName("isTokenBlacklisted should return true when key exists in Redis")
    void isTokenBlacklisted_whenKeyExists_shouldReturnTrue() {
        var jti = "blacklisted-jti";
        var expectedKey = blacklistPrefix + jti;

        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);

        var isBlacklisted = blacklistService.isTokenBlacklisted(jti);

        assertTrue(isBlacklisted);
        verify(redisTemplate).hasKey(expectedKey);
    }

    @Test
    @DisplayName("isTokenBlacklisted should return false when key does not exist in Redis")
    void isTokenBlacklisted_whenKeyDoesNotExist_shouldReturnFalse() {
        var jti = "clean-jti";
        var expectedKey = blacklistPrefix + jti;

        when(redisTemplate.hasKey(expectedKey)).thenReturn(false);

        var isBlacklisted = blacklistService.isTokenBlacklisted(jti);

        assertFalse(isBlacklisted);
        verify(redisTemplate).hasKey(expectedKey);
    }
}
