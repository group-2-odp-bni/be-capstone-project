package com.bni.orange.wallet.client;

import com.bni.orange.wallet.exception.business.ExternalServiceException;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.users.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserClient {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient userWebClient;

    public UserProfileResponse findUserById(UUID userId) {
        log.info("Fetching user profile for userId: {}", userId);
        var context = "find user by id: " + userId;

        try {
            return userWebClient.get()
                .uri("/internal/v1/user/{id}", userId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, handleClientError(context))
                .onStatus(HttpStatusCode::is5xxServerError, handleServerError(context))
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .retryWhen(buildRetrySpec(context))
                .map(ApiResponse::getData)
                .doOnSuccess(user -> log.info("Successfully fetched user: {}", userId))
                .doOnError(err -> handleError(context, err))
                .block();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error for {}: {}", context, e.getMessage(), e);
            throw ExternalServiceException.userServiceNotAvailable(e.getMessage());
        }
    }

    public UserProfileResponse findUserByPhone(String phoneE164) {
        log.info("Fetching user by phone: {}", phoneE164);
        var context = "find user by phone: " + phoneE164;

        try {
            return userWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/internal/v1/user/by-phone")
                    .queryParam("phone", phoneE164)
                    .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, handleClientError(context))
                .onStatus(HttpStatusCode::is5xxServerError, handleServerError(context))
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {
                })
                .timeout(REQUEST_TIMEOUT)
                .retryWhen(buildRetrySpec(context))
                .map(ApiResponse::getData)
                .doOnSuccess(user -> log.info("Successfully fetched user by phone: {}", phoneE164))
                .doOnError(err -> handleError(context, err))
                .block();
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error for {}: {}", context, e.getMessage(), e);
            throw ExternalServiceException.userServiceNotAvailable(e.getMessage());
        }
    }

    private Function<ClientResponse, Mono<? extends Throwable>> handleClientError(String context) {
        return response -> response.bodyToMono(String.class)
            .switchIfEmpty(Mono.just("[no body]"))
            .flatMap(body -> {
                log.error("Client error for {}: {}", context, body);
                return Mono.error(new ExternalServiceException.ClientErrorException(body));
            });
    }

    private Function<ClientResponse, Mono<? extends Throwable>> handleServerError(String context) {
        return response -> response.bodyToMono(String.class)
            .switchIfEmpty(Mono.just("[no body]"))
            .flatMap(body -> {
                log.error("Server error for {}: {}", context, body);
                return Mono.error(new ExternalServiceException.ServerErrorException(body));
            });
    }

    private Retry buildRetrySpec(String context) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
            .filter(this::isRetryableException)
            .doBeforeRetry(retrySignal ->
                log.warn("Retrying {}: attempt {}", context, retrySignal.totalRetries() + 1));
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientException) {
            int statusCode = webClientException.getStatusCode().value();
            return statusCode >= 500 && statusCode < 600;
        }
        return throwable instanceof java.net.ConnectException
            || throwable instanceof java.util.concurrent.TimeoutException
            || throwable instanceof io.netty.handler.timeout.ReadTimeoutException;
    }

    public UserProfileResponse getUserProfile(UUID userId) {
        return findUserById(userId);
    }

    public UserProfileResponse getUserByPhone(String phoneE164) {
        return findUserByPhone(phoneE164);
    }

    private void handleError(String context, Throwable err) {
        if (!(err instanceof ExternalServiceException)) {
            log.error("Failed to complete {}: {}", context, err.getMessage());
        }
    }
}
