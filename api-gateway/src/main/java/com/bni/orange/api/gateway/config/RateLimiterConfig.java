package com.bni.orange.api.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Optional;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"))
                .map(ip -> ip.split(",")[0].trim())
                .map(Mono::just)
                .orElse(Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(address -> address.getAddress().getHostAddress())
                        .orElse("127.0.0.1")));
    }
}
