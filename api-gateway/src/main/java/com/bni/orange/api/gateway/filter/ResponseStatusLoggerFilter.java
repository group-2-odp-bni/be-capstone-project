package com.bni.orange.api.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Debug filter to log response status codes
 */
@Slf4j
@Component
public class ResponseStatusLoggerFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain
            .filter(exchange)
            .doFinally(signalType -> {
                var statusCode = exchange.getResponse().getStatusCode();
                if (statusCode != null) {
                    log.debug("Response status: {} for path: {}",
                        statusCode.value(),
                        exchange.getRequest().getPath());
                }
            });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
