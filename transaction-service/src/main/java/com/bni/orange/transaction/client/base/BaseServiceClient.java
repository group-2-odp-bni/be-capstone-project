package com.bni.orange.transaction.client.base;

import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.response.ApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Base abstract class for all service clients.
 * Provides common functionality for:
 * - WebClient operations with resilience patterns
 * - Error handling and mapping
 * - Response unwrapping
 * - Circuit breaker and retry patterns
 */
@Slf4j
public abstract class BaseServiceClient {

    protected final WebClient webClient;
    protected final Retry retry;
    protected final CircuitBreaker circuitBreaker;
    private final String serviceName;

    protected BaseServiceClient(
        WebClient webClient,
        Retry retry,
        CircuitBreaker circuitBreaker,
        String serviceName
    ) {
        this.webClient = webClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.serviceName = serviceName;
    }

    /**
     * Execute a GET request and unwrap the ApiResponse.
     * Applies retry and circuit breaker patterns automatically.
     *
     * @param uriFunction Function to build the URI and headers
     * @param responseType Response type reference
     * @param errorMapper Custom error mapper for specific error codes
     * @param <T> Response data type
     * @return Mono of unwrapped data
     */
    protected <T> Mono<T> executeGet(
        Function<WebClient.RequestHeadersUriSpec<?>, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType,
        Function<WebClientResponseException, Throwable> errorMapper
    ) {
        log.debug("Executing GET request to {}", serviceName);

        return Mono.defer(() -> {
                WebClient.RequestHeadersSpec<?> requestSpec = uriFunction.apply(webClient.get());
                return requestSpec.retrieve()
                    .bodyToMono(responseType)
                    .map(ApiResponse::data);
            })
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .onErrorResume(WebClientResponseException.class, ex -> {
                if (errorMapper != null) {
                    var mappedError = errorMapper.apply(ex);
                    if (mappedError != null) {
                        return Mono.error(mappedError);
                    }
                }
                return Mono.error(mapWebClientException(ex));
            });
    }

    /**
     * Simplified GET request without custom error mapping.
     */
    protected <T> Mono<T> executeGet(
        Function<WebClient.RequestHeadersUriSpec<?>, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType
    ) {
        return executeGet(uriFunction, responseType, null);
    }

    /**
     * Execute a POST request and unwrap the ApiResponse.
     * Applies retry and circuit breaker patterns automatically.
     *
     * @param uriFunction Function to build the URI, headers and body
     * @param responseType Response type reference
     * @param errorMapper Custom error mapper
     * @param <T> Response data type
     * @return Mono of unwrapped data
     */
    protected <T> Mono<T> executePost(
        Function<WebClient.RequestBodyUriSpec, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType,
        Function<WebClientResponseException, Throwable> errorMapper
    ) {
        log.debug("Executing POST request to {}", serviceName);

        return Mono.defer(() -> {
                WebClient.RequestHeadersSpec<?> requestSpec = uriFunction.apply(webClient.post());
                return requestSpec.retrieve()
                    .bodyToMono(responseType)
                    .map(ApiResponse::data);
            })
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .onErrorResume(WebClientResponseException.class, ex -> {
                if (errorMapper != null) {
                    var mappedError = errorMapper.apply(ex);
                    if (mappedError != null) {
                        return Mono.error(mappedError);
                    }
                }
                return Mono.error(mapWebClientException(ex));
            });
    }

    /**
     * Simplified POST request without custom error mapping.
     */
    protected <T> Mono<T> executePost(
        Function<WebClient.RequestBodyUriSpec, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType
    ) {
        return executePost(uriFunction, responseType, null);
    }

    /**
     * Default WebClient exception mapper.
     * Subclasses can override to provide service-specific error codes.
     */
    protected Throwable mapWebClientException(WebClientResponseException ex) {
        log.error("{} error: {} - {}", serviceName, ex.getStatusCode(), ex.getResponseBodyAsString());
        return new BusinessException(
            getServiceErrorCode(),
            String.format("Error communicating with %s", serviceName)
        );
    }

    /**
     * Get the error code to use for this service.
     * Override this method to return service-specific error codes.
     */
    protected abstract ErrorCode getServiceErrorCode();

    /**
     * Helper to create NOT_FOUND error mapper.
     */
    protected Function<WebClientResponseException, Throwable> notFoundMapper(ErrorCode errorCode, String message) {
        return ex -> {
            if (ex instanceof WebClientResponseException.NotFound) {
                log.warn("{}: {}", serviceName, message);
                return new BusinessException(errorCode, message);
            }
            return null;
        };
    }

    /**
     * Helper to create UNAUTHORIZED error mapper.
     */
    protected Function<WebClientResponseException, Throwable> unauthorizedMapper(ErrorCode errorCode, String message) {
        return ex -> {
            if (ex instanceof WebClientResponseException.Unauthorized) {
                log.warn("{}: {}", serviceName, message);
                return new BusinessException(errorCode, message);
            }
            return null;
        };
    }

    /**
     * Helper to create CONFLICT error mapper.
     */
    protected Function<WebClientResponseException, Throwable> conflictMapper(ErrorCode errorCode, String message) {
        return ex -> {
            if (ex instanceof WebClientResponseException.Conflict) {
                log.error("{}: {}", serviceName, message);
                return new BusinessException(errorCode, message);
            }
            return null;
        };
    }

    /**
     * Combine multiple error mappers.
     */
    @SafeVarargs
    protected final Function<WebClientResponseException, Throwable> combineMappers(
        Function<WebClientResponseException, Throwable>... mappers
    ) {
        return ex -> {
            for (var mapper : mappers) {
                var result = mapper.apply(ex);
                if (result != null) {
                    return result;
                }
            }
            return null;
        };
    }
}
