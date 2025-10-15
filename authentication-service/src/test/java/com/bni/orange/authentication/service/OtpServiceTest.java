package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    private static final int MAX_OTP_ATTEMPTS = 3;

    @InjectMocks
    private OtpService otpService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisPrefixProperties redisProperties;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisPrefixProperties.Prefix prefix;

    private final String phoneNumber = "+6281234567890";
    private String otpKey, cooldownKey, failCountKey, lockedKey;

    @BeforeEach
    void setUp() {
        when(redisProperties.prefix()).thenReturn(prefix);
        when(prefix.otp()).thenReturn("otp:");
        when(prefix.otpCooldown()).thenReturn("otp_cooldown:");
        when(prefix.otpFailCount()).thenReturn("otp_fail_count:");
        when(prefix.otpLocked()).thenReturn("otp_locked:");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        otpKey = "otp:" + phoneNumber;
        cooldownKey = "otp_cooldown:" + phoneNumber;
        failCountKey = "otp_fail_count:" + phoneNumber;
        lockedKey = "otp_locked:" + phoneNumber;
    }

    @Test
    @DisplayName("generateAndStoreOtp should throw exception if account is locked")
    void generateAndStoreOtp_whenLocked_shouldThrowException() {
        when(redisTemplate.hasKey(lockedKey)).thenReturn(true);

        var exception = assertThrows(BusinessException.class, () -> otpService.generateAndStoreOtp(phoneNumber));

        assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("generateAndStoreOtp should generate and store a 6-digit OTP")
    void generateAndStoreOtp_whenNotLocked_shouldGenerateAndStoreOtp() {
        when(redisTemplate.hasKey(lockedKey)).thenReturn(false);

        var otp = otpService.generateAndStoreOtp(phoneNumber);

        assertNotNull(otp);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
        verify(valueOperations).set(eq(otpKey), eq(otp), any(Duration.class));
    }

    @Test
    @DisplayName("isOtpValid should throw exception if account is locked")
    void isOtpValid_whenLocked_shouldThrowException() {
        when(redisTemplate.hasKey(lockedKey)).thenReturn(true);

        var exception = assertThrows(BusinessException.class, () -> otpService.isOtpValid(phoneNumber, "123456"));

        assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
    }

    @Test
    @DisplayName("isOtpValid should return true and clear failures for correct OTP")
    void isOtpValid_withCorrectOtp_shouldReturnTrueAndClearFailures() {
        var correctOtp = "123456";
        when(redisTemplate.hasKey(lockedKey)).thenReturn(false);
        when(valueOperations.get(otpKey)).thenReturn(correctOtp);

        var isValid = otpService.isOtpValid(phoneNumber, correctOtp);

        assertTrue(isValid);
        verify(redisTemplate).delete(otpKey);
        verify(redisTemplate).delete(failCountKey);
        verify(redisTemplate).delete(lockedKey);
    }

    @Test
    @DisplayName("isOtpValid should return false and increment failures for incorrect OTP")
    void isOtpValid_withIncorrectOtp_shouldReturnFalseAndIncrementFailures() {
        var correctOtp = "123456";
        var incorrectOtp = "654321";
        when(redisTemplate.hasKey(lockedKey)).thenReturn(false);
        when(valueOperations.get(otpKey)).thenReturn(correctOtp);
        when(valueOperations.increment(failCountKey)).thenReturn(1L);

        var isValid = otpService.isOtpValid(phoneNumber, incorrectOtp);

        assertFalse(isValid);
        verify(valueOperations).increment(failCountKey); // Verifies incrementOtpFailures
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("isOtpValid should lock account on max incorrect attempts")
    void isOtpValid_onMaxAttempts_shouldLockAccountAndThrow() {
        var correctOtp = "123456";
        var incorrectOtp = "654321";
        when(redisTemplate.hasKey(lockedKey)).thenReturn(false);
        when(valueOperations.get(otpKey)).thenReturn(correctOtp);
        when(valueOperations.increment(failCountKey)).thenReturn((long) MAX_OTP_ATTEMPTS);

        var exception = assertThrows(BusinessException.class, () -> otpService.isOtpValid(phoneNumber, incorrectOtp));

        assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
        verify(valueOperations).set(eq(lockedKey), eq("true"), any(Duration.class));
        verify(redisTemplate).delete(failCountKey);
    }

    @Test
    @DisplayName("isCooldown should return true if cooldown key exists")
    void isCooldown_whenKeyExists_shouldReturnTrue() {
        when(redisTemplate.hasKey(cooldownKey)).thenReturn(true);
        assertTrue(otpService.isCooldown(phoneNumber));
    }

    @Test
    @DisplayName("isCooldown should return false if cooldown key does not exist")
    void isCooldown_whenKeyDoesNotExist_shouldReturnFalse() {
        when(redisTemplate.hasKey(cooldownKey)).thenReturn(false);
        assertFalse(otpService.isCooldown(phoneNumber));
    }

    @Test
    @DisplayName("setCooldown should set the cooldown key in Redis with correct duration")
    void setCooldown_shouldSetKeyWithDuration() {
        otpService.setCooldown(phoneNumber);
        verify(valueOperations).set(eq(cooldownKey), eq("true"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("getOtpAttemptsLeft should return correct count when key exists")
    void getOtpAttemptsLeft_whenKeyExists_shouldReturnCorrectCount() {
        when(valueOperations.get(failCountKey)).thenReturn("1");

        var attemptsLeft = otpService.getOtpAttemptsLeft(phoneNumber);

        assertEquals(MAX_OTP_ATTEMPTS - 1, attemptsLeft);
    }

    @Test
    @DisplayName("getOtpAttemptsLeft should return max attempts when key does not exist")
    void getOtpAttemptsLeft_whenKeyDoesNotExist_shouldReturnMaxAttempts() {
        when(valueOperations.get(failCountKey)).thenReturn(null);

        var attemptsLeft = otpService.getOtpAttemptsLeft(phoneNumber);

        assertEquals(MAX_OTP_ATTEMPTS, attemptsLeft);
    }
}
