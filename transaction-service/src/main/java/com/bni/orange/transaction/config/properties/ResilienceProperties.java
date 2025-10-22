package com.bni.orange.transaction.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "resilience")
public record ResilienceProperties(
    @DefaultValue
    RetryProperties retry,
    @DefaultValue
    CircuitBreakerProperties circuitBreaker
) {

    public record RetryProperties(
        int maxAttempts,
        Duration initialBackoff,
        double multiplier,
        Duration maxBackoff,
        boolean failAfterMaxAttempts,
        List<Class<? extends Throwable>> retryableExceptions
    ) {
    }

    public record CircuitBreakerProperties(
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        Duration waitDurationInOpenState,
        int permittedNumberOfCallsInHalfOpenState,
        boolean automaticTransitionFromOpenToHalfOpenEnabled
    ) {
    }
}
