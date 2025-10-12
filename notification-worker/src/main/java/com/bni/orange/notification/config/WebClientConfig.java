package com.bni.orange.notification.config;

import com.bni.orange.notification.config.properties.WahaConfigProperties;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(WahaConfigProperties.class)
public class WebClientConfig {

    private final WahaConfigProperties wahaConfig;

    @Bean
    public WebClient wahaWebClient() {
        return WebClient.builder()
            .baseUrl(wahaConfig.baseUrl())
            .build();
    }

    @Bean
    public Retry wahaApiRetry() {
        Predicate<Throwable> isRetryableException = throwable ->
            throwable instanceof WebClientResponseException.InternalServerError ||
                throwable instanceof WebClientResponseException.ServiceUnavailable ||
                throwable instanceof TimeoutException;

        var retryConfig = RetryConfig.<Throwable>custom()
            .maxAttempts(wahaConfig.retry().maxAttempts())
            .waitDuration(wahaConfig.retry().backoffDuration())
            .retryOnException(isRetryableException)
            .failAfterMaxAttempts(true)
            .build();

        return Retry.of("wahaApi", retryConfig);
    }
}