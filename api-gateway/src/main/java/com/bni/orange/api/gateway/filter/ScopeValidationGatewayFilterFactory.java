package com.bni.orange.api.gateway.filter;

import com.bni.orange.api.gateway.exception.JwtAuthenticationException;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ScopeValidationGatewayFilterFactory extends AbstractGatewayFilterFactory<ScopeValidationGatewayFilterFactory.Config> {

    private final ReactiveJwtDecoder jwtDecoder;

    public ScopeValidationGatewayFilterFactory(ReactiveJwtDecoder jwtDecoder) {
        super(Config.class);
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            Optional<String> tokenOptional = extractBearerToken(exchange);
            return tokenOptional.map(s -> jwtDecoder.decode(s)
                .flatMap(jwt -> {
                    List<String> scopes = jwt.getClaimAsStringList("scope");
                    if (scopes == null || !scopes.contains(config.getRequiredScope())) {
                        log.warn("Token does not have required scope. Required: {}, Actual: {}. Path: {}",
                            config.getRequiredScope(), scopes, exchange.getRequest().getPath());
                        ServerHttpResponse response = exchange.getResponse();
                        response.setStatusCode(HttpStatus.FORBIDDEN);
                        return response.setComplete();
                    }
                    return chain.filter(exchange);
                })
                .onErrorResume(JwtException.class, e -> {
                    log.debug("JWT validation failed during scope check: {}", e.getMessage());
                    return Mono.error(new JwtAuthenticationException("TOKEN_INVALID", "Invalid token for scope validation"));
                })).orElseGet(() -> Mono.error(JwtAuthenticationException.tokenMissing()));

        };
    }

    private Optional<String> extractBearerToken(org.springframework.web.server.ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("Authorization"))
            .filter(header -> header.startsWith("Bearer "))
            .map(header -> header.substring(7));
    }

    @Validated
    @Getter
    @Setter
    public static class Config {
        @NotEmpty
        private String requiredScope;
    }
}
