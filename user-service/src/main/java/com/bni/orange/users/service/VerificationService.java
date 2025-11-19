package com.bni.orange.users.service;

import com.bni.orange.users.config.properties.KafkaTopicProperties;
import com.bni.orange.users.config.properties.RedisPrefixProperties;
import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.bni.orange.users.event.EventPublisher;
import com.bni.orange.users.event.ProfileEventFactory;
import com.bni.orange.users.model.enums.TokenType;
import com.bni.orange.users.util.OtpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final StringRedisTemplate redisTemplate;
    private final EventPublisher eventPublisher;
    private final KafkaTopicProperties topicProperties;
    private final RedisPrefixProperties redisProperties;
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TOKENS_PER_WINDOW = 3;

    public void generateEmailOtp(UUID userId, String email) {
        log.info("Generating email OTP for user: {}", userId);

        checkRateLimit(userId, TokenType.EMAIL);

        if (isLocked(userId, TokenType.EMAIL)) {
            throw new BusinessException(ErrorCode.OTP_LOCKED, "Account temporarily locked. Please try again later");
        }

        var otpKey = getOtpKey(userId, TokenType.EMAIL);
        redisTemplate.delete(otpKey);

        var otpCode = OtpGenerator.generate();
        redisTemplate.opsForValue().set(otpKey, otpCode, OTP_VALIDITY);

        var metaKey = getMetaKey(userId, TokenType.EMAIL);
        redisTemplate.opsForValue().set(metaKey, email, OTP_VALIDITY);

        publishEmailOtpEvent(userId, email, otpCode);

        log.info("Email OTP generated successfully for user: {}", userId);
    }


    public void generatePhoneOtp(UUID userId, String phoneNumber) {
        log.info("Generating phone OTP for user: {}", userId);

        checkRateLimit(userId, TokenType.PHONE);

        if (isLocked(userId, TokenType.PHONE)) {
            throw new BusinessException(ErrorCode.OTP_LOCKED, "Account temporarily locked. Please try again later");
        }

        var otpKey = getOtpKey(userId, TokenType.PHONE);
        redisTemplate.delete(otpKey);

        var otpCode = OtpGenerator.generate();
        redisTemplate.opsForValue().set(otpKey, otpCode, OTP_VALIDITY);

        var metaKey = getMetaKey(userId, TokenType.PHONE);
        redisTemplate.opsForValue().set(metaKey, phoneNumber, OTP_VALIDITY);

        publishPhoneOtpEvent(userId, phoneNumber, otpCode);

        log.info("Phone OTP generated successfully for user: {}", userId);
    }

    public String verifyOtp(UUID userId, TokenType tokenType, String otpCode) {
        log.info("Verifying {} OTP for user: {}", tokenType, userId);

        var otpKey = getOtpKey(userId, tokenType);
        var storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            throw new BusinessException(ErrorCode.OTP_NOT_FOUND, "OTP not found or expired");
        }

        if (isLocked(userId, tokenType)) {
            var lockKey = getLockKey(userId, tokenType);
            var ttl = redisTemplate.getExpire(lockKey);
            throw new BusinessException(
                ErrorCode.OTP_LOCKED,
                String.format("Too many failed attempts. Try again in %d seconds", ttl),
                createLockDetails(ttl)
            );
        }

        if (!storedOtp.equals(otpCode)) {
            boolean maxAttemptsReached = incrementFailureCount(userId, tokenType);

            if (maxAttemptsReached) {
                log.warn("Max OTP attempts reached for user: {}, account locked", userId);
                throw new BusinessException(
                    ErrorCode.OTP_LOCKED,
                    "Maximum verification attempts exceeded. Account temporarily locked",
                    createLockDetails(LOCK_DURATION.getSeconds())
                );
            }

            var attemptsLeft = getRemainingAttempts(userId, tokenType);
            log.warn("Invalid OTP code for user: {}, attempts remaining: {}", userId, attemptsLeft);
            throw new BusinessException(
                ErrorCode.OTP_INVALID,
                String.format("Invalid OTP code. %d attempts remaining", attemptsLeft)
            );
        }

        var metaKey = getMetaKey(userId, tokenType);
        var newValue = redisTemplate.opsForValue().get(metaKey);

        clearOtpData(userId, tokenType);

        log.info("{} OTP verified successfully for user: {}", tokenType, userId);
        return newValue;
    }

    private void checkRateLimit(UUID userId, TokenType tokenType) {
        var rateLimitKey = getRateLimitKey(userId, tokenType);
        var count = redisTemplate.opsForValue().get(rateLimitKey);

        if (count != null && Integer.parseInt(count) >= MAX_TOKENS_PER_WINDOW) {
            log.warn("Rate limit exceeded for user: {}, type: {}", userId, tokenType);
            throw new BusinessException(
                ErrorCode.RATE_LIMIT_EXCEEDED,
                String.format("Too many OTP requests. Please wait %d minutes before trying again", RATE_LIMIT_WINDOW.toMinutes())
            );
        }

        if (count == null) {
            redisTemplate.opsForValue().set(rateLimitKey, "1", RATE_LIMIT_WINDOW);
        } else {
            redisTemplate.opsForValue().increment(rateLimitKey);
        }
    }

    private boolean incrementFailureCount(UUID userId, TokenType tokenType) {
        var failKey = getFailCountKey(userId, tokenType);
        var attempts = redisTemplate.opsForValue().increment(failKey);

        if (attempts != null && attempts >= MAX_OTP_ATTEMPTS) {
            // Lock the account
            var lockKey = getLockKey(userId, tokenType);
            redisTemplate.opsForValue().set(lockKey, "true", LOCK_DURATION);
            redisTemplate.delete(failKey);
            return true;
        } else {
            redisTemplate.expire(failKey, LOCK_DURATION);
            return false;
        }
    }

    private boolean isLocked(UUID userId, TokenType tokenType) {
        var lockKey = getLockKey(userId, tokenType);
        return redisTemplate.hasKey(lockKey);
    }


    private int getRemainingAttempts(UUID userId, TokenType tokenType) {
        var failKey = getFailCountKey(userId, tokenType);
        var attempts = Optional.ofNullable(redisTemplate.opsForValue().get(failKey))
            .map(Long::parseLong)
            .orElse(0L);
        return (int) Math.max(0, MAX_OTP_ATTEMPTS - attempts);
    }

    /**
     * Clear all OTP-related data from Redis for a user.
     * This includes: OTP code, metadata, failure count, and lock status.
     *
     * @param userId User ID
     * @param tokenType Type of OTP (EMAIL or PHONE)
     */
    public void clearOtpData(UUID userId, TokenType tokenType) {
        redisTemplate.delete(getOtpKey(userId, tokenType));
        redisTemplate.delete(getMetaKey(userId, tokenType));
        redisTemplate.delete(getFailCountKey(userId, tokenType));
        redisTemplate.delete(getLockKey(userId, tokenType));
        log.debug("Cleared OTP data for user: {}, type: {}", userId, tokenType);
    }

    private void publishEmailOtpEvent(UUID userId, String email, String otpCode) {
        try {
            var topicName = topicProperties.definitions().get("otp-email-notification").name();
            var event = ProfileEventFactory.createOtpEmailEvent(email, otpCode, userId);
            eventPublisher.publish(topicName, userId.toString(), event);
            log.debug("Email OTP notification event published for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish email OTP event for user: {}", userId, e);
        }
    }

    private void publishPhoneOtpEvent(UUID userId, String phoneNumber, String otpCode) {
        try {
            var topicName = topicProperties.definitions().get("otp-whatsapp-notification").name();
            var event = ProfileEventFactory.createOtpPhoneEvent(phoneNumber, otpCode, userId);
            eventPublisher.publish(topicName, userId.toString(), event);
            log.debug("Phone OTP notification event published for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish phone OTP event for user: {}", userId, e);
        }
    }

    private Map<String, Object> createLockDetails(Long secondsRemaining) {
        var details = new HashMap<String, Object>();
        details.put("lockedUntilSeconds", secondsRemaining);
        return details;
    }

    private String getOtpKey(UUID userId, TokenType tokenType) {
        return (tokenType == TokenType.EMAIL ? redisProperties.prefix().otpEmail() : redisProperties.prefix().otpPhone()) + userId;
    }

    private String getMetaKey(UUID userId, TokenType tokenType) {
        return getOtpKey(userId, tokenType) + ":meta";
    }

    private String getFailCountKey(UUID userId, TokenType tokenType) {
        return (tokenType == TokenType.EMAIL ? redisProperties.prefix().otpEmailFailCount() : redisProperties.prefix().otpPhoneFailCount()) + userId;
    }

    private String getLockKey(UUID userId, TokenType tokenType) {
        return (tokenType == TokenType.EMAIL ? redisProperties.prefix().otpEmailLocked() : redisProperties.prefix().otpPhoneLocked()) + userId;
    }

    /**
     * Get remaining OTP generation attempts within the rate limit window.
     *
     * @param userId User ID
     * @param tokenType Type of OTP (EMAIL or PHONE)
     * @return Number of remaining attempts (0 to MAX_TOKENS_PER_WINDOW)
     */
    public int getRemainingOtpGenerationAttempts(UUID userId, TokenType tokenType) {
        var rateLimitKey = getRateLimitKey(userId, tokenType);
        var count = redisTemplate.opsForValue().get(rateLimitKey);

        if (count == null) {
            return MAX_TOKENS_PER_WINDOW;
        }

        int used = Integer.parseInt(count);
        return Math.max(0, MAX_TOKENS_PER_WINDOW - used);
    }

    /**
     * Get TTL (time to live) in seconds for the current rate limit window.
     *
     * @param userId User ID
     * @param tokenType Type of OTP (EMAIL or PHONE)
     * @return TTL in seconds, or 0 if no rate limit is active
     */
    public long getRateLimitResetInSeconds(UUID userId, TokenType tokenType) {
        var rateLimitKey = getRateLimitKey(userId, tokenType);
        var ttl = redisTemplate.getExpire(rateLimitKey);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    private String getRateLimitKey(UUID userId, TokenType tokenType) {
        return getOtpKey(userId, tokenType) + ":ratelimit";
    }
}
