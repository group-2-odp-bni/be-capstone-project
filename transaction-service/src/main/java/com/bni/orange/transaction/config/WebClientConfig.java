package com.bni.orange.transaction.config;

import com.bni.orange.transaction.config.properties.ResilienceProperties;
import com.bni.orange.transaction.config.properties.ServiceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({ResilienceProperties.class, ServiceProperties.class})
public class WebClientConfig {

    private final ResilienceProperties resilienceProps;
    private final ServiceProperties serviceProps;

    @Bean
    public WebClient userServiceWebClient() {
        return createWebClient(serviceProps.getUserService().getUrl(), "user-service");
    }

    @Bean
    public WebClient walletServiceWebClient() {
        return createWebClient(serviceProps.getWalletService().getUrl(), "wallet-service");
    }

    @Bean
    public WebClient authServiceWebClient() {
        return createWebClient(serviceProps.getAuthenticationService().getUrl(), "authentication-service");
    }

    private WebClient createWebClient(String baseUrl, String serviceName) {
        var timeout = serviceProps.getUserService().getTimeout();
        var httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
            .responseTimeout(Duration.ofMillis(timeout))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
            );

        return WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter((request, next) -> {
                log.debug("Calling {}: {} {}", serviceName, request.method(), request.url());
                return next.exchange(request)
                    .doOnSuccess(response -> log.debug("{} response: {}", serviceName, response.statusCode()))
                    .doOnError(error -> log.error("Error calling {}: {}", serviceName, error.getMessage()));
            })
            .filter((request, next) ->
                ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .filter(authentication -> authentication instanceof JwtAuthenticationToken)
                    .map(authentication -> (JwtAuthenticationToken) authentication)
                    .map(jwtAuthentication -> {
                        log.debug("Attaching bearer token to {} request", serviceName);
                        return ClientRequest.from(request)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuthentication.getToken().getTokenValue())
                            .build();
                    })
                    .switchIfEmpty(Mono.just(request))
                    .flatMap(next::exchange))
            .build();
    }

    @Bean
    public Retry externalServiceRetry() {
        var retryProps = resilienceProps.retry();

        var retryConfig = RetryConfig.<Throwable>custom()
            .maxAttempts(retryProps.maxAttempts())
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                retryProps.initialBackoff(),
                retryProps.multiplier(),
                retryProps.maxBackoff()
            ))
            .retryOnException(ex -> retryProps.retryableExceptions().stream()
                .anyMatch(exceptionClass -> exceptionClass.isInstance(ex)))
            .failAfterMaxAttempts(retryProps.failAfterMaxAttempts())
            .build();

        var retry = Retry.of("externalService", retryConfig);

        retry.getEventPublisher()
            .onRetry(event -> log.warn(
                "Retry attempt {} for external service call. Last exception: {}",
                event.getNumberOfRetryAttempts(),
                Objects.requireNonNull(event.getLastThrowable()).getMessage()
            ));

        return retry;
    }

    @Bean
    public CircuitBreaker externalServiceCircuitBreaker() {
        var cbProps = resilienceProps.circuitBreaker();

        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(cbProps.slidingWindowSize())
            .minimumNumberOfCalls(cbProps.minimumNumberOfCalls())
            .failureRateThreshold(cbProps.failureRateThreshold())
            .waitDurationInOpenState(cbProps.waitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(cbProps.permittedNumberOfCallsInHalfOpenState())
            .automaticTransitionFromOpenToHalfOpenEnabled(cbProps.automaticTransitionFromOpenToHalfOpenEnabled())
            .build();

        var circuitBreaker = CircuitBreaker.of("externalService", circuitBreakerConfig);

        circuitBreaker
            .getEventPublisher()
            .onStateTransition(event -> log.warn(
                "Circuit breaker state changed from {} to {}",
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()
            ));

        return circuitBreaker;
    }
}
