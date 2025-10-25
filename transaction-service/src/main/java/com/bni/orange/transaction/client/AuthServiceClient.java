package com.bni.orange.transaction.client;

import com.bni.orange.transaction.client.base.BaseServiceClient;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.PinVerifyResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
public class AuthServiceClient extends BaseServiceClient {

    public AuthServiceClient(
        WebClient authServiceWebClient,
        Retry externalServiceRetry,
        CircuitBreaker externalServiceCircuitBreaker
    ) {
        super(authServiceWebClient, externalServiceRetry, externalServiceCircuitBreaker, "authentication-service");
    }

    @Override
    protected ErrorCode getServiceErrorCode() {
        return ErrorCode.AUTH_SERVICE_ERROR;
    }

    public Mono<Boolean> verifyPin(String pin, String accessToken) {
        var requestBody = Map.of("pin", pin);

        return executePost(
            uriSpec -> uriSpec
                .uri("/api/v1/pin/verify")
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(requestBody),
            new ParameterizedTypeReference<ApiResponse<PinVerifyResponse>>() {},
            unauthorizedMapper(ErrorCode.INVALID_PIN, "Invalid PIN provided")
        ).map(response -> response != null && Boolean.TRUE.equals(response.valid()));
    }
}
