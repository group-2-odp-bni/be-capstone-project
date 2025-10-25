package com.bni.orange.transaction.client;

import com.bni.orange.transaction.client.base.BaseServiceClient;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class UserServiceClient extends BaseServiceClient {

    public UserServiceClient(
        WebClient userServiceWebClient,
        Retry externalServiceRetry,
        CircuitBreaker externalServiceCircuitBreaker
    ) {
        super(userServiceWebClient, externalServiceRetry, externalServiceCircuitBreaker, "user-service");
    }

    @Override
    protected ErrorCode getServiceErrorCode() {
        return ErrorCode.USER_SERVICE_ERROR;
    }

    public Mono<UserProfileResponse> findByPhoneNumber(String phoneNumber, String accessToken) {
        log.debug("Finding user by phone number: {}", phoneNumber);

        return executeGet(
            uriSpec -> uriSpec
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/user/by-phone")
                    .queryParam("phone", phoneNumber)
                    .build())
                .header("Authorization", "Bearer " + accessToken),
            new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {},
            notFoundMapper(ErrorCode.USER_NOT_FOUND, "User not found with phone number: " + phoneNumber)
        );
    }
}
