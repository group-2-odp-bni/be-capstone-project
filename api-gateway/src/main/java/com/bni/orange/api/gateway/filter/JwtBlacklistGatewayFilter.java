package com.bni.orange.api.gateway.filter;

import com.bni.orange.api.gateway.exception.JwtAuthenticationException;
import com.bni.orange.api.gateway.exception.TokenRevokedException;
import com.bni.orange.api.gateway.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtBlacklistGatewayFilter implements GlobalFilter, Ordered {

    private final BlacklistService blacklistService;
    private final ReactiveJwtDecoder reactiveJwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final String path = exchange.getRequest().getURI().getPath();

        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        return extractBearerToken(exchange)
            .map(token -> reactiveJwtDecoder.decode(token)
                .doOnNext(jwt -> {
                    if (Objects.isNull(jwt.getId())) {
                        log.warn("JWT without JTI claim detected. Token might be invalid.");
                    }
                })
                .map(JwtClaimAccessor::getId)
                .flatMap(jti -> blacklistService
                    .isTokenBlacklisted(jti)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            log.warn("Blacklisted token attempted. JTI: {}, IP: {}, Path: {}", jti, exchange.getRequest().getRemoteAddress(), path);
                            return Mono.error(new TokenRevokedException("This token has been revoked and is no longer valid"));
                        }
                        return chain.filter(exchange);
                    })
                )
                .onErrorResume(JwtException.class, e -> {
                    log.debug("JWT validation failed: {}", e.getMessage());
                    return Mono.error(handleJwtException(e));
                })
            )
            .orElseGet(() -> Mono.error(JwtAuthenticationException.tokenMissing()));
    }

    private JwtAuthenticationException handleJwtException(JwtException ex) {
        return switch (ex) {
            case JwtValidationException jve when jve.getMessage().contains("Jwt expired") -> {
                log.debug("Token expired");
                yield JwtAuthenticationException.tokenExpired();
            }
            case BadJwtException ignored -> {
                log.debug("Bad JWT token: {}", ex.getMessage());
                yield JwtAuthenticationException.tokenInvalid();
            }
            case JwtEncodingException ignored -> {
                log.debug("JWT encoding error: {}", ex.getMessage());
                yield JwtAuthenticationException.tokenMalformed();
            }
            default -> {
                log.debug("JWT validation failed: {}", ex.getMessage());
                yield JwtAuthenticationException.tokenInvalid();
            }
        };
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/v1/auth/request") ||
            path.startsWith("/api/v1/auth/verify") ||
            path.startsWith("/api/v1/auth/refresh") ||
            path.startsWith("/api/v1/auth/resend-otp") ||
            path.startsWith("/api/v1/pin/reset/request") ||
            path.startsWith("/api/v1/pin/reset/verify") ||
            path.startsWith("/oauth2/jwks");
    }

    private Optional<String> extractBearerToken(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("Authorization"))
            .filter(header -> header.startsWith("Bearer "))
            .map(header -> header.substring(7));
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
