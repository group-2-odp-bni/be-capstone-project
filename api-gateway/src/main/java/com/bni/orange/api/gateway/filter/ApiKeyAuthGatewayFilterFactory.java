package com.bni.orange.api.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
public class ApiKeyAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<ApiKeyAuthGatewayFilterFactory.Config> {

    @Value("${orange.payment.bni.api-key:bni-api-key-dev}")
    private String bniApiKey;

    public ApiKeyAuthGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var path = exchange.getRequest().getURI().getPath();
            log.debug("ApiKeyAuthGatewayFilter checking path: {}", path);

            var apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");

            if (Objects.isNull(apiKey) || apiKey.isEmpty()) {
                log.warn("Missing X-API-Key header for path: {}, IP: {}", path, exchange.getRequest().getRemoteAddress());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            if (!isValidApiKey(apiKey)) {
                log.warn("Invalid X-API-Key for path: {}, IP: {}", path, exchange.getRequest().getRemoteAddress());
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            log.debug("API key validation successful for path: {}", path);
            return chain.filter(exchange);
        };
    }

    private boolean isValidApiKey(String apiKey) {
        if (Objects.isNull(bniApiKey) || bniApiKey.isEmpty()) {
            log.error("BNI API key not configured in application properties");
            return false;
        }
        var isValid = bniApiKey.equals(apiKey);
        if (!isValid) {
            log.warn("API key mismatch - Expected: {}, Received: {}",
                bniApiKey.substring(0, Math.min(8, bniApiKey.length())) + "***",
                apiKey != null ? apiKey.substring(0, Math.min(8, apiKey.length())) + "***" : "null");
        }
        return isValid;
    }

    public static class Config {
    }
}
