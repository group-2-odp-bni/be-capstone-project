package com.bni.orange.wallet.client;

import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.users.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InternalUserClient {

    private final @Qualifier("internalUserWebClient") WebClient webClient;

    public UserProfileResponse getUserProfile(UUID userId) {
        ApiResponse<UserProfileResponse> resp = webClient.get()
            .uri("/internal/v1/user/{id}", userId)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {})
            .block();

        if (resp == null || resp.getData() == null) {
            throw new IllegalStateException("User profile not found: " + userId);
        }

        return resp.getData();
    }

    public UserProfileResponse getUserByPhone(String phoneE164) {
        ApiResponse<UserProfileResponse> resp = webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/internal/v1/user/by-phone")
                .queryParam("phone", phoneE164)
                .build())
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {})
            .block();

        if (resp == null || resp.getData() == null) {
            throw new IllegalStateException("User not found: " + phoneE164);
        }

        return resp.getData();
    }
}