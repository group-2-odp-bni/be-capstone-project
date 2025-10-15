package com.bni.orange.api.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Boolean> isTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }
        var key = BLACKLIST_PREFIX + jti;
        return redisTemplate.hasKey(key);
    }
}
