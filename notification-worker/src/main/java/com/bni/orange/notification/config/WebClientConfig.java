package com.bni.orange.notification.config;

import com.bni.orange.notification.config.properties.WahaConfigProperties;
import com.bni.orange.notification.config.properties.WebhookConfigProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({WahaConfigProperties.class, WebhookConfigProperties.class})
public class WebClientConfig {

    private final WahaConfigProperties wahaConfig;

    @Bean
    public WebClient wahaWebClient() {
        var httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) wahaConfig.timeout().toMillis())
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(wahaConfig.timeout().toSeconds(), TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(wahaConfig.timeout().toSeconds(), TimeUnit.SECONDS))
            );

        return WebClient.builder()
            .baseUrl(wahaConfig.baseUrl())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .defaultHeader("X-Api-Key", wahaConfig.apiKey())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Bean
    public Retry wahaApiRetry() {
        Predicate<Throwable> isRetryableException = throwable ->
            throwable instanceof WebClientResponseException.InternalServerError ||
                throwable instanceof WebClientResponseException.ServiceUnavailable ||
                throwable instanceof WebClientResponseException.BadGateway ||
                throwable instanceof WebClientResponseException.GatewayTimeout ||
                throwable instanceof TimeoutException;

        var retryConfig = RetryConfig.<Throwable>custom()
            .maxAttempts(wahaConfig.retry().maxAttempts())
            .waitDuration(wahaConfig.retry().initialBackoff())
            .intervalFunction(io.github.resilience4j.core.IntervalFunction
                .ofExponentialRandomBackoff(
                    wahaConfig.retry().initialBackoff(),
                    wahaConfig.retry().multiplier(),
                    wahaConfig.retry().maxBackoff()
                ))
            .retryOnException(isRetryableException)
            .failAfterMaxAttempts(true)
            .build();

        Retry retry = Retry.of("wahaApi", retryConfig);

        retry.getEventPublisher()
            .onRetry(event -> log.warn(
                "Retry attempt {} for WAHA API call. Last exception: {}",
                event.getNumberOfRetryAttempts(),
                Objects.requireNonNull(event.getLastThrowable()).getMessage()
            ));

        return retry;
    }

    @Bean
    public CircuitBreaker wahaApiCircuitBreaker() {
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("wahaApi", circuitBreakerConfig);

        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.warn(
                "Circuit breaker state changed from {} to {}",
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()
            ));

        return circuitBreaker;
    }
}