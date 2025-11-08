package com.bni.orange.notification.client;

import com.bni.orange.notification.model.response.ApiResponse;
import com.bni.orange.notification.model.response.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserClient {

    private final @Qualifier("userWebClient") WebClient webClient;

    public Mono<UserProfileResponse> findUserById(String userId) {
        return this.webClient.get()
                .uri("/internal/v1/user/{id}", userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {})
                .map(ApiResponse::getData);
    }

    public Mono<UserProfileResponse> findUserByPhone(String phoneE164) {
        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/v1/user/by-phone")
                        .queryParam("phone", phoneE164)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {})
                .map(ApiResponse::getData);
    }
}
