package com.bni.orange.api.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.Optional;


@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                .map(forwardedFor -> forwardedFor.split(",")[0].trim())
                .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(address -> address.getAddress().getHostAddress())
                    .orElse("127.0.0.1"));

            return Mono.just("ip:" + ip);
        };
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .filter(principal -> principal instanceof Authentication)
            .cast(Authentication.class)
            .map(Authentication::getPrincipal)
            .filter(principal -> principal instanceof Jwt)
            .cast(Jwt.class)
            .map(jwt -> jwt.getClaimAsString("sub"))
            .map(userId -> "user:" + userId)
            .switchIfEmpty(Mono.defer(() -> {
                // Fallback to IP if not authenticated
                String ip = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                    .map(forwardedFor -> forwardedFor.split(",")[0].trim())
                    .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(address -> address.getAddress().getHostAddress())
                        .orElse("127.0.0.1"));

                return Mono.just("ip:" + ip);
            }));
    }

    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String ip = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                .map(forwardedFor -> forwardedFor.split(",")[0].trim())
                .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(address -> address.getAddress().getHostAddress())
                    .orElse("127.0.0.1"));

            String path = exchange.getRequest().getPath().value();

            return Mono.just("path:" + ip + ":" + path);
        };
    }

    @Bean
    public KeyResolver compositeKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .filter(principal -> principal instanceof Authentication)
            .cast(Authentication.class)
            .map(Authentication::getPrincipal)
            .filter(principal -> principal instanceof Jwt)
            .cast(Jwt.class)
            .map(jwt -> {
                var userId = jwt.getClaimAsString("sub");
                var ip = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                    .map(forwardedFor -> forwardedFor.split(",")[0].trim())
                    .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(address -> address.getAddress().getHostAddress())
                        .orElse("127.0.0.1"));

                return "composite:" + userId + ":" + ip;
            })
            .switchIfEmpty(Mono.defer(() -> {
                var ip = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                    .map(forwardedFor -> forwardedFor.split(",")[0].trim())
                    .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(address -> address.getAddress().getHostAddress())
                        .orElse("127.0.0.1"));

                return Mono.just("ip:" + ip);
            }));
    }
}
