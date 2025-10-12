package com.bni.orange.api.gateway.filter;

import com.bni.orange.api.gateway.service.BlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtBlacklistGatewayFilter implements GlobalFilter, Ordered {

    private final BlacklistService blacklistService;
    private final JwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getURI().getPath();

        // Skip blacklist check for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        var token = extractBearerToken(exchange);

        if (token == null) {
            // No token, let Spring Security handle it
            return chain.filter(exchange);
        }

        try {
            var jwt = jwtDecoder.decode(token);
            var jti = jwt.getId();

            if (jti == null) {
                log.warn("JWT without JTI claim detected. Token might be invalid.");
                return chain.filter(exchange);
            }

            // Check if token is blacklisted
            return blacklistService.isTokenBlacklisted(jti)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        log.warn("Blacklisted token attempted. JTI: {}, IP: {}, Path: {}",
                            jti,
                            request.getRemoteAddress(),
                            path
                        );
                        return respondUnauthorized(exchange, "TOKEN_REVOKED", "This token has been revoked");
                    }
                    return chain.filter(exchange);
                });

        } catch (JwtException e) {
            // Invalid JWT, let Spring Security handle it (will return 401)
            log.debug("JWT validation failed in blacklist filter: {}", e.getMessage());
            return chain.filter(exchange);
        }
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

    private String extractBearerToken(ServerWebExchange exchange) {
        var authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private Mono<Void> respondUnauthorized(ServerWebExchange exchange, String code, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        var errorJson = String.format(
            "{\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}",
            code,
            message
        );

        var buffer = response.bufferFactory().wrap(errorJson.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Run before Spring Security filter (order -100)
        return -200;
    }
}
