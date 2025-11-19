package com.bni.orange.notification.config;

import com.bni.orange.notification.config.properties.ResilienceProperties;
import com.bni.orange.notification.config.properties.UserServiceConfigProperties;
import com.bni.orange.notification.config.properties.WahaConfigProperties;
import com.bni.orange.notification.config.properties.WebhookConfigProperties;
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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
    WahaConfigProperties.class,
    WebhookConfigProperties.class,
    ResilienceProperties.class,
    UserServiceConfigProperties.class
})
public class WebClientConfig {

    private final WahaConfigProperties wahaConfig;
    private final UserServiceConfigProperties userServiceConfig;
    private final ResilienceProperties resilienceProps;

    private HttpClient createHttpClientWithTimeout(Duration timeout) {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(timeout.toSeconds(), TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeout.toSeconds(), TimeUnit.SECONDS))
            );
    }

    @Bean
    public WebClient userWebClient() {
        var httpClient = createHttpClientWithTimeout(userServiceConfig.timeout());

        return WebClient.builder()
            .baseUrl(userServiceConfig.baseUrl())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Bean
    public WebClient wahaWebClient() {
        var httpClient = createHttpClientWithTimeout(wahaConfig.timeout());

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

        var retry = Retry.of("wahaApi", retryConfig);

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
        var cbProps = resilienceProps.circuitBreaker();

        var circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(cbProps.slidingWindowSize())
            .minimumNumberOfCalls(cbProps.minimumNumberOfCalls())
            .failureRateThreshold(cbProps.failureRateThreshold())
            .waitDurationInOpenState(cbProps.waitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(cbProps.permittedNumberOfCallsInHalfOpenState())
            .automaticTransitionFromOpenToHalfOpenEnabled(cbProps.automaticTransitionFromOpenToHalfOpenEnabled())
            .build();

        var circuitBreaker = CircuitBreaker.of("wahaApi", circuitBreakerConfig);

        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.warn(
                "Circuit breaker state changed from {} to {}",
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()
            ));

        return circuitBreaker;
    }
}