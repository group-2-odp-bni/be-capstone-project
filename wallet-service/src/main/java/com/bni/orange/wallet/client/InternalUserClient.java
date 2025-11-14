package com.bni.orange.wallet.client;

import com.bni.orange.wallet.model.response.users.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InternalUserClient {

    private final RestTemplate restTemplate;

    @Value("${app.internal.user-service-base-url}")
    private String userServiceBaseUrl;

    public UserProfileResponse getUserProfile(UUID userId) {
        String url = userServiceBaseUrl + "/internal/v1/user/" + userId;
        var response = restTemplate.getForObject(url, ApiResponseUserProfile.class);
        if (response == null || response.getData() == null) {
            throw new IllegalStateException("Failed to fetch user profile for id=" + userId);
        }
        return response.getData();
    }

    public static class ApiResponseUserProfile {
        private UserProfileResponse data;
        public UserProfileResponse getData() { return data; }
        public void setData(UserProfileResponse data) { this.data = data; }
    }
}
