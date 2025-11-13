package com.bni.orange.authentication.service.captcha;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CaptchaAttemptService {

    private static final String CAPTCHA_CACHE_PREFIX = "captcha:used_tokens:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(2);

    private final ReactiveRedisTemplate<String, String> redis;

    public Mono<Boolean> isTokenAlreadyUsed(String token) {
        return Mono.justOrEmpty(token)
            .filter(t -> !t.isBlank())
            .flatMap(t -> redis.opsForValue()
                .setIfAbsent(CAPTCHA_CACHE_PREFIX + t, "used", TOKEN_TTL)
                .map(wasSet -> !wasSet)
            )
            .defaultIfEmpty(true);
    }
}