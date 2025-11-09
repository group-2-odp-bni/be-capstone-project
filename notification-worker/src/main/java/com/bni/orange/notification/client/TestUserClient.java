package com.bni.orange.notification.client;

import com.bni.orange.notification.model.response.UserProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Primary
@Component
public class TestUserClient extends UserClient {

    public TestUserClient(@Qualifier("userWebClient") WebClient webClient) {
        super(webClient);
    }

    private static final String MOCK_USER_ID = "33333333-3333-3333-3333-333333333339";
    private static final String MOCK_PHONE_NUMBER = "+6282210472710";

    @Override
    public Mono<UserProfileResponse> findUserById(String userId) {
        log.info("MOCK findUserById");
        if (MOCK_USER_ID.equals(userId)) {
            return Mono.just(UserProfileResponse.builder()
                    .userId(UUID.fromString(userId))
                    .phoneNumber(MOCK_PHONE_NUMBER)
                    .fullName("Mocked User B")
                    .build());
        }
        return Mono.empty();
    }

    @Override
    public Mono<UserProfileResponse> findUserByPhone(String phoneE164) {
        log.info("MOCK findUserByPhone");
        if (MOCK_PHONE_NUMBER.equals(phoneE164)) {
            return Mono.just(UserProfileResponse.builder()
                    .userId(UUID.fromString(MOCK_USER_ID))
                    .phoneNumber(MOCK_PHONE_NUMBER)
                    .fullName("Mocked User B")
                    .build());
        }
        return Mono.empty();
    }
}
