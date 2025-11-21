package com.bni.orange.api.gateway.filter;

import com.bni.orange.api.gateway.config.SecurityProperties;
import com.bni.orange.api.gateway.service.IpBlockingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;

@Slf4j
@Component
@Profile("!load-test")
@RequiredArgsConstructor
public class IpBlockingGatewayFilter implements GlobalFilter, Ordered {

    private final IpBlockingService ipBlockingService;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var ipAddress = extractIpAddress(exchange);

        if (securityProperties.whitelistedIps() != null && securityProperties.whitelistedIps().contains(ipAddress)) {
            log.trace("IP {} is whitelisted, skipping IP blocking check.", ipAddress);
            return chain.filter(exchange);
        }

        return ipBlockingService
            .isIpBlocked(ipAddress)
            .flatMap(isBlocked -> {
                if (isBlocked) {
                    log.warn("Blocking request from blacklisted IP: {}", ipAddress);
                    return ipBlockingService
                        .getBlockTimeRemaining(ipAddress)
                        .flatMap(remainingSeconds -> sendBlockedResponse(exchange, ipAddress, remainingSeconds));
                }
                return chain.filter(exchange);
            });
    }

    private String extractIpAddress(ServerWebExchange exchange) {
        return Optional
            .ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
            .map(ip -> ip.split(",")[0].trim())
            .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse("unknown"));
    }

    private Mono<Void> sendBlockedResponse(ServerWebExchange exchange, String ipAddress, Long remainingSeconds) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        var errorResponse = new HashMap<String, Object>();
        errorResponse.put("status", "error");
        errorResponse.put("code", "IP_BLOCKED");
        errorResponse.put("message", "Your IP address has been temporarily blocked due to suspicious activity");
        errorResponse.put("ipAddress", ipAddress);
        errorResponse.put("retryAfterSeconds", remainingSeconds);
        errorResponse.put("timestamp", System.currentTimeMillis());

        try {
            var bytes = objectMapper.writeValueAsBytes(errorResponse);
            var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error creating blocked response", e);
            var fallbackBytes = "{\"status\":\"error\",\"message\":\"Access denied\"}".getBytes(StandardCharsets.UTF_8);
            var buffer = exchange.getResponse().bufferFactory().wrap(fallbackBytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
