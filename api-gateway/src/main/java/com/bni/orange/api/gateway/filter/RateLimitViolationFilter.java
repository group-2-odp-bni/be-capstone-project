package com.bni.orange.api.gateway.filter;

import com.bni.orange.api.gateway.config.SecurityProperties;
import com.bni.orange.api.gateway.service.IpBlockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * Filter to detect and record rate limit violations.
 * Automatically blocks IPs that repeatedly violate rate limits.
 */
@Slf4j
@Component
@Profile("!load-test")
@RequiredArgsConstructor
public class RateLimitViolationFilter implements GlobalFilter, Ordered {

    private final IpBlockingService ipBlockingService;
    private final SecurityProperties securityProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ipAddress = extractIpAddress(exchange);

        if (securityProperties.whitelistedIps() != null && securityProperties.whitelistedIps().contains(ipAddress)) {
            log.trace("IP {} is whitelisted, skipping rate limit violation check.", ipAddress);
            return chain.filter(exchange);
        }

        return chain
            .filter(exchange)
            .publishOn(Schedulers.boundedElastic())
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
        return Optional
            .ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
            .map(ip -> ip.split(",")[0].trim())
            .orElse(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse("unknown"));
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
