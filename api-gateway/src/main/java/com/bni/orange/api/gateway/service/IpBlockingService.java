package com.bni.orange.api.gateway.service;

import com.bni.orange.api.gateway.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityProperties.class)
public class IpBlockingService {

    private static final String BLOCKED_IP_PREFIX = "security:blocked:ip:";
    private static final String VIOLATION_COUNT_PREFIX = "security:violations:";
    private static final String SUSPICIOUS_IP_PREFIX = "security:suspicious:ip:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    public Mono<Boolean> isIpBlocked(String ipAddress) {
        if (!securityProperties.ipBlockingEnabled()) {
            return Mono.just(false);
        }

        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.just(false);
        }

        if (isLocalhost(ipAddress) && !securityProperties.allowLocalhostBlocking()) {
            return Mono.just(false);
        }

        var key = BLOCKED_IP_PREFIX + ipAddress;
        return redisTemplate
            .hasKey(key)
            .doOnNext(blocked -> {
                if (blocked) {
                    log.warn("Blocked IP attempted access: {}", ipAddress);
                }
            });
    }

    public Mono<Boolean> isIpSuspicious(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.just(false);
        }

        var key = SUSPICIOUS_IP_PREFIX + ipAddress;
        return redisTemplate.hasKey(key);
    }

    public Mono<Void> recordViolation(String ipAddress) {
        if (!securityProperties.ipBlockingEnabled()) {
            return Mono.empty();
        }

        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.empty();
        }

        if (isLocalhost(ipAddress) && !securityProperties.allowLocalhostBlocking()) {
            log.debug("Skipping violation recording for localhost IP: {}", ipAddress);
            return Mono.empty();
        }

        var countKey = VIOLATION_COUNT_PREFIX + ipAddress;

        return redisTemplate
            .opsForValue()
            .increment(countKey)
            .flatMap(count -> {
                log.info("Violation recorded for IP {}: count = {}/{}", ipAddress, count, securityProperties.violationThreshold());

                if (count == 1) {
                    return redisTemplate.expire(countKey, securityProperties.violationWindow())
                        .then(Mono.just(count));
                }
                return Mono.just(count);
            })
            .flatMap(count -> {
                if (count >= securityProperties.violationThreshold()) {
                    log.warn("IP {} exceeded violation threshold ({}). Blocking for {}",
                        ipAddress, count, securityProperties.blockDuration());
                    return blockIp(ipAddress, "Exceeded rate limit violations: " + count);
                } else if (count >= securityProperties.suspiciousThreshold()) {
                    return markAsSuspicious(ipAddress);
                }
                return Mono.empty();
            })
            .then();
    }

    public Mono<Void> blockIp(String ipAddress, String reason) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.empty();
        }

        if (isLocalhost(ipAddress) && !securityProperties.allowLocalhostBlocking()) {
            log.warn("Attempted to block localhost IP {} - blocked by configuration", ipAddress);
            return Mono.empty();
        }

        var key = BLOCKED_IP_PREFIX + ipAddress;
        var suspiciousKey = SUSPICIOUS_IP_PREFIX + ipAddress;

        log.error("BLOCKING IP: {} - Reason: {}", ipAddress, reason);

        return redisTemplate.opsForValue()
            .set(key, reason, securityProperties.blockDuration())
            .then(redisTemplate.delete(suspiciousKey))
            .then();
    }

    public Mono<Void> markAsSuspicious(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.empty();
        }

        if (isLocalhost(ipAddress) && !securityProperties.allowLocalhostBlocking()) {
            return Mono.empty();
        }

        var key = SUSPICIOUS_IP_PREFIX + ipAddress;

        return redisTemplate.hasKey(key)
            .flatMap(exists -> {
                if (!exists) {
                    log.warn("Marking IP as suspicious: {}", ipAddress);
                    return redisTemplate.opsForValue()
                        .set(key, String.valueOf(System.currentTimeMillis()), securityProperties.suspiciousDuration())
                        .then();
                }
                return Mono.empty();
            });
    }

    private boolean isLocalhost(String ipAddress) {
        return "127.0.0.1".equals(ipAddress) ||
            "0:0:0:0:0:0:0:1".equals(ipAddress) ||
            "::1".equals(ipAddress) ||
            "localhost".equalsIgnoreCase(ipAddress);
    }

    public Mono<Boolean> unblockIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.just(false);
        }

        var blockKey = BLOCKED_IP_PREFIX + ipAddress;
        var countKey = VIOLATION_COUNT_PREFIX + ipAddress;
        var suspiciousKey = SUSPICIOUS_IP_PREFIX + ipAddress;

        log.info("Unblocking IP: {}", ipAddress);

        return redisTemplate.delete(blockKey, countKey, suspiciousKey)
            .map(deleted -> deleted > 0);
    }

    public Mono<Long> getViolationCount(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.just(0L);
        }

        var key = VIOLATION_COUNT_PREFIX + ipAddress;
        return redisTemplate.opsForValue()
            .get(key)
            .map(Long::parseLong)
            .defaultIfEmpty(0L);
    }

    public Mono<Long> getBlockTimeRemaining(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return Mono.just(0L);
        }

        var key = BLOCKED_IP_PREFIX + ipAddress;
        return redisTemplate.getExpire(key)
            .map(Duration::getSeconds)
            .defaultIfEmpty(0L);
    }
}
