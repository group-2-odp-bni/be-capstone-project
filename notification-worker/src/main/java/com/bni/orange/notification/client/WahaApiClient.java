package com.bni.orange.notification.client;

import com.bni.orange.notification.config.properties.WahaConfigProperties;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class WahaApiClient {

    private final WebClient webClient;
    private final WahaConfigProperties config;
    private final Retry retry;

    private record WahaSendMessageRequest(
        String chatId,
        String text,
        String session
    ) {
    }

    public Mono<Void> sendTextMessage(String phoneNumber, String message) {
        var chatId = phoneNumber.replace("+", "") + "@c.us";
        var requestBody = new WahaSendMessageRequest(chatId, message, config.sessionName());

        return webClient.post()
            .uri("/api/sendText")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Void.class)
            .transform(RetryOperator.of(retry))
            .doOnError(err -> log.error("Failed to send WA message to {} after retries", phoneNumber, err))
            .doOnSuccess(v -> log.info("Successfully sent request to WAHA for phone number {}", phoneNumber));
    }
}