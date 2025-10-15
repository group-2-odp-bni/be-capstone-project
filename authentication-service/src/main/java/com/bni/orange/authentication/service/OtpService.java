package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom random = new SecureRandom();
    private static final int OTP_MAX_VALUE = 999999;
    private static final int MAX_OTP_ATTEMPTS = 3;
    private static final int OTP_LOCK_DURATION_MINUTES = 15;
    private static final Duration OTP_VALIDITY_DURATION = Duration.ofMinutes(3);
    private static final Duration OTP_COOLDOWN_DURATION = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final RedisPrefixProperties redisProperties;

    public String generateAndStoreOtp(String phoneNumber) {
        if (isOtpLocked(phoneNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        var otp = String.format("%06d", random.nextInt(OTP_MAX_VALUE));
        redisTemplate.opsForValue().set(redisProperties.prefix().otp() + phoneNumber, otp, OTP_VALIDITY_DURATION);
        return otp;
    }

    public boolean isOtpValid(String phoneNumber, String otp) {
        if (isOtpLocked(phoneNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        var storedOtp = redisTemplate.opsForValue().get(redisProperties.prefix().otp() + phoneNumber);

        if (otp.equals(storedOtp)) {
            redisTemplate.delete(redisProperties.prefix().otp() + phoneNumber);
            clearOtpFailures(phoneNumber);
            return true;
        }

        incrementOtpFailures(phoneNumber);
        return false;
    }

    public boolean isCooldown(String phoneNumber) {
        return redisTemplate.hasKey(redisProperties.prefix().otpCooldown() + phoneNumber);
    }

    public void setCooldown(String phoneNumber) {
        redisTemplate.opsForValue().set(redisProperties.prefix().otpCooldown() + phoneNumber, "true", OTP_COOLDOWN_DURATION);
    }

    private boolean isOtpLocked(String phoneNumber) {
        return redisTemplate.hasKey(redisProperties.prefix().otpLocked() + phoneNumber);
    }

    private void incrementOtpFailures(String phoneNumber) {
        var failKey = redisProperties.prefix().otpFailCount() + phoneNumber;
        var attempts = redisTemplate.opsForValue().increment(failKey);

        if (attempts != null && attempts >= MAX_OTP_ATTEMPTS) {
            redisTemplate.opsForValue().set(
                redisProperties.prefix().otpLocked() + phoneNumber,
                "true",
                Duration.ofMinutes(OTP_LOCK_DURATION_MINUTES)
            );
            redisTemplate.delete(failKey);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        } else {
            redisTemplate.expire(failKey, Duration.ofMinutes(OTP_LOCK_DURATION_MINUTES));
        }
    }

    private void clearOtpFailures(String phoneNumber) {
        redisTemplate.delete(redisProperties.prefix().otpFailCount() + phoneNumber);
        redisTemplate.delete(redisProperties.prefix().otpLocked() + phoneNumber);
    }

    public int getOtpAttemptsLeft(String phoneNumber) {
        var failKey = redisProperties.prefix().otpFailCount() + phoneNumber;
        var attempts = Optional.ofNullable(redisTemplate.opsForValue().get(failKey))
            .map(Long::parseLong)
            .orElse(0L);
        return (int) Math.max(0, MAX_OTP_ATTEMPTS - attempts);
    }
}