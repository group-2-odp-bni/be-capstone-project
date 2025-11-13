package com.bni.orange.api.gateway.filter;

import com.bni.orange.api.gateway.service.IpBlockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Filter to detect and record rate limit violations.
 * Automatically blocks IPs that repeatedly violate rate limits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitViolationFilter implements GlobalFilter, Ordered {

    private final IpBlockingService ipBlockingService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ipAddress = extractIpAddress(exchange);

        return chain.filter(exchange)
            .doOnSuccess(unused -> {
                var statusCode = exchange.getResponse().getStatusCode();

                if (statusCode != null && statusCode.value() == 429) {
                    log.warn("Rate limit violation detected from IP: {}", ipAddress);

                    ipBlockingService.recordViolation(ipAddress)
                        .doOnError(error -> log.error("Failed to record violation for IP: {}", ipAddress, error))
                        .subscribe();
                }
            });
    }

    private String extractIpAddress(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
            .map(ip -> ip.split(",")[0].trim())
            .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse("unknown"));
    }

    @Override
    public int getOrder() {
        // Run after rate limiting filter but before response is written
        // NettyWriteResponseFilter runs at -1, so we need to be before that
        return -2;
    }
}
