package com.bni.orange.transaction.client.base;

import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.internal.InternalApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.function.Function;

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

    private <T, R> Mono<T> executeRequest(
        HttpMethod method,
        Function<WebClient, WebClient.RequestHeadersSpec<?>> requestSpecFunction,
        ParameterizedTypeReference<R> responseWrapperType,
        Function<R, T> dataExtractor,
        Function<WebClientResponseException, Throwable> errorMapper
    ) {
        log.debug("Executing {} request to {}", method, serviceName);

        var requestSpec = requestSpecFunction.apply(webClient);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            log.debug("Attaching Bearer token from SecurityContextHolder.");
            requestSpec.headers(headers -> headers.setBearerAuth(jwtAuth.getToken().getTokenValue()));
        } else {
            log.warn("No JWT authentication found in SecurityContextHolder. Type is: {}", auth != null ? auth.getClass().getName() : "null");
        }

        return requestSpec.retrieve()
            .bodyToMono(responseWrapperType)
            .map(dataExtractor)
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

    protected <T> Mono<T> executeGet(
        Function<WebClient.RequestHeadersUriSpec<?>, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType,
        Function<WebClientResponseException, Throwable> errorMapper
    ) {
        return executeRequest(
            HttpMethod.GET,
            client -> uriFunction.apply(client.get()),
            responseType,
            ApiResponse::getData,
            errorMapper
        );
    }

    protected <T> Mono<T> executeGet(
        Function<WebClient.RequestHeadersUriSpec<?>, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType
    ) {
        return executeGet(uriFunction, responseType, null);
    }

    protected <T> Mono<T> executePost(
        Function<WebClient.RequestBodyUriSpec, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType,
        Function<WebClientResponseException, Throwable> errorMapper
    ) {
        return executeRequest(
            HttpMethod.POST,
            client -> uriFunction.apply(client.post()),
            responseType,
            ApiResponse::getData,
            errorMapper
        );
    }

    protected <T> Mono<T> executePost(
        Function<WebClient.RequestBodyUriSpec, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<ApiResponse<T>> responseType
    ) {
        return executePost(uriFunction, responseType, null);
    }

    protected <T> Mono<T> executeGetInternal(
        Function<WebClient.RequestHeadersUriSpec<?>, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<InternalApiResponse<T>> responseType,
        Function<WebClientResponseException, Throwable> errorMapper
    ) {
        return executeRequest(
            HttpMethod.GET,
            client -> uriFunction.apply(client.get()),
            responseType,
            InternalApiResponse::getData,
            errorMapper
        );
    }

    protected <T> Mono<T> executeGetInternal(
        Function<WebClient.RequestHeadersUriSpec<?>, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<InternalApiResponse<T>> responseType
    ) {
        return executeGetInternal(uriFunction, responseType, null);
    }

    protected <T> Mono<T> executePostInternal(
        Function<WebClient.RequestBodyUriSpec, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<InternalApiResponse<T>> responseType,
        Function<WebClientResponseException, Throwable> errorMapper
    ) {
        return executeRequest(
            HttpMethod.POST,
            client -> uriFunction.apply(client.post()),
            responseType,
            InternalApiResponse::getData,
            errorMapper
        );
    }

    protected <T> Mono<T> executePostInternal(
        Function<WebClient.RequestBodyUriSpec, WebClient.RequestHeadersSpec<?>> uriFunction,
        ParameterizedTypeReference<InternalApiResponse<T>> responseType
    ) {
        return executePostInternal(uriFunction, responseType, null);
    }

    protected Throwable mapWebClientException(WebClientResponseException ex) {
        log.error("{} error: {} - {}", serviceName, ex.getStatusCode(), ex.getResponseBodyAsString());
        return new BusinessException(
            getServiceErrorCode(),
            "Error communicating with %s".formatted(serviceName)
        );
    }

    protected abstract ErrorCode getServiceErrorCode();

    protected Function<WebClientResponseException, Throwable> notFoundMapper(ErrorCode errorCode, String message) {
        return ex -> {
            if (ex instanceof WebClientResponseException.NotFound) {
                log.warn("{}: {}", serviceName, message);
                return new BusinessException(errorCode, message);
            }
            return null;
        };
    }

    protected Function<WebClientResponseException, Throwable> unauthorizedMapper(ErrorCode errorCode, String message) {
        return ex -> {
            if (ex instanceof WebClientResponseException.Unauthorized) {
                log.warn("{}: {}", serviceName, message);
                return new BusinessException(errorCode, message);
            }
            return null;
        };
    }

    protected Function<WebClientResponseException, Throwable> conflictMapper(ErrorCode errorCode, String message) {
        return ex -> {
            if (ex instanceof WebClientResponseException.Conflict) {
                log.error("{}: {}", serviceName, message);
                return new BusinessException(errorCode, message);
            }
            return null;
        };
    }
}
