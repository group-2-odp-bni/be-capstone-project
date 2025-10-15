package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    private static final int MAX_ATTEMPTS = 5;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisPrefixProperties redisProperties;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisPrefixProperties.Prefix prefix;

    private final String userKey = "user123";
    private String failKey;
    private String lockKey;

    @BeforeEach
    void setUp() {
        String failCountPrefix = "pin_fail_count:";
        failKey = failCountPrefix + userKey;
        String lockedPrefix = "pin_locked:";
        lockKey = lockedPrefix + userKey;

        when(redisProperties.prefix()).thenReturn(prefix);
        when(prefix.pinFailCount()).thenReturn(failCountPrefix);
        when(prefix.pinLocked()).thenReturn(lockedPrefix);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("loginFailed should increment attempts and set expiry when below max")
    void loginFailed_whenAttemptsBelowMax_shouldIncrementAndExpire() {
        when(valueOperations.increment(failKey)).thenReturn(3L);

        loginAttemptService.loginFailed(userKey);

        verify(valueOperations).increment(failKey);
        verify(redisTemplate).expire(failKey, Duration.ofMinutes(15));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, never()).delete(failKey);
    }

    @Test
    @DisplayName("loginFailed should lock account when attempts reach max")
    void loginFailed_whenAttemptsReachMax_shouldLockAccount() {
        when(valueOperations.increment(failKey)).thenReturn((long) MAX_ATTEMPTS);

        loginAttemptService.loginFailed(userKey);

        verify(valueOperations).increment(failKey);
        verify(valueOperations).set(lockKey, "true", Duration.ofMinutes(15));
        verify(redisTemplate).delete(failKey);
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("loginFailed should set expiry when increment returns null")
    void loginFailed_whenIncrementReturnsNull_shouldSetExpiry() {
        when(valueOperations.increment(failKey)).thenReturn(null);

        loginAttemptService.loginFailed(userKey);

        verify(valueOperations).increment(failKey);
        verify(redisTemplate).expire(failKey, Duration.ofMinutes(15));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, never()).delete(failKey);
    }

    @Test
    @DisplayName("loginSucceeded should delete fail and lock keys")
    void loginSucceeded_shouldDeleteKeys() {
        loginAttemptService.loginSucceeded(userKey);

        verify(redisTemplate).delete(failKey);
        verify(redisTemplate).delete(lockKey);
    }

    @Test
    @DisplayName("isLocked should return true when lock key exists")
    void isLocked_whenKeyExists_shouldReturnTrue() {
        when(redisTemplate.hasKey(lockKey)).thenReturn(true);

        var isLocked = loginAttemptService.isLocked(userKey);

        assertTrue(isLocked);
    }

    @Test
    @DisplayName("isLocked should return false when lock key does not exist")
    void isLocked_whenKeyDoesNotExist_shouldReturnFalse() {
        when(redisTemplate.hasKey(lockKey)).thenReturn(false);

        var isLocked = loginAttemptService.isLocked(userKey);

        assertFalse(isLocked);
    }

    @Test
    @DisplayName("getAttemptsLeft should return correct count when key exists")
    void getAttemptsLeft_whenKeyExists_shouldReturnCorrectCount() {
        when(valueOperations.get(failKey)).thenReturn("2");

        var attemptsLeft = loginAttemptService.getAttemptsLeft(userKey);

        assertEquals(MAX_ATTEMPTS - 2, attemptsLeft);
    }

    @Test
    @DisplayName("getAttemptsLeft should return max attempts when key does not exist")
    void getAttemptsLeft_whenKeyDoesNotExist_shouldReturnMaxAttempts() {
        when(valueOperations.get(failKey)).thenReturn(null);

        var attemptsLeft = loginAttemptService.getAttemptsLeft(userKey);

        assertEquals(MAX_ATTEMPTS, attemptsLeft);
    }

    @Test
    @DisplayName("applyProgressiveDelay should not sleep when attempts are below threshold")
    void applyProgressiveDelay_belowThreshold_shouldNotSleep() {
        when(valueOperations.get(failKey)).thenReturn("1");

        assertDoesNotThrow(() -> loginAttemptService.applyProgressiveDelay(userKey));
    }

    @Test
    @DisplayName("applyProgressiveDelay should trigger sleep when attempts are at or above threshold")
    void applyProgressiveDelay_aboveThreshold_shouldTriggerSleep() {
        when(valueOperations.get(failKey)).thenReturn("3");

        assertDoesNotThrow(() -> loginAttemptService.applyProgressiveDelay(userKey));
    }

    @Test
    @DisplayName("applyProgressiveDelay should handle interruption during sleep")
    void applyProgressiveDelay_whenInterrupted_shouldHandleGracefully() {
        when(valueOperations.get(failKey)).thenReturn("4");

        Thread.currentThread().interrupt();

        loginAttemptService.applyProgressiveDelay(userKey);

        assertTrue(Thread.interrupted(), "Interrupted status should be true after catching InterruptedException");
    }
}
