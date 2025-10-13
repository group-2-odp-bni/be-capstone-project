package com.bni.orange.notification.client;

import com.bni.orange.notification.config.properties.WahaConfigProperties;
import com.bni.orange.notification.dto.WahaMessageResponse;
import com.bni.orange.notification.dto.WahaQRCodeResponse;
import com.bni.orange.notification.dto.WahaSessionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class WahaApiClient {

    private final WebClient webClient;
    private final WahaConfigProperties config;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    private record WahaSendMessageRequest(
        String chatId,
        String text,
        String session,
        Boolean linkPreview
    ) {
    }

    public Mono<WahaMessageResponse> sendTextMessage(String phoneNumber, String message) {
        var chatId = formatChatId(phoneNumber);
        var requestBody = new WahaSendMessageRequest(chatId, message, config.sessionName(), false);

        log.info("Sending WhatsApp message to {} via WAHA", phoneNumber);

        return webClient.post()
            .uri("/api/sendText")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Client error sending message to {}: {}", phoneNumber, body);
                        return Mono.error(new WahaClientException("Client error: " + body));
                    })
            )
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("Server error from WAHA: {}", body);
                        return Mono.error(new WahaServiceException("WAHA service error: " + body));
                    })
            )
            .bodyToMono(WahaMessageResponse.class)
            .timeout(Duration.ofSeconds(30))
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .doOnSuccess(response ->
                log.info("Successfully sent WhatsApp message to {}. Message ID: {}", phoneNumber, response.id())
            )
            .doOnError(err ->
                log.error("Failed to send WhatsApp message to {} after all retries: {}", phoneNumber, err.getMessage())
            );
    }

    public Mono<WahaSessionResponse> getSessionStatus() {
        return webClient.get()
            .uri("/api/sessions/{session}", config.sessionName())
            .retrieve()
            .bodyToMono(WahaSessionResponse.class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(session -> log.info("Session status: {}", session.status()))
            .doOnError(err -> log.error("Failed to get session status", err));
    }

    public Mono<Void> startSession() {
        return webClient.post()
            .uri("/api/sessions/{session}/start", config.sessionName())
            .retrieve()
            .bodyToMono(Void.class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(v -> log.info("Session started successfully"))
            .doOnError(err -> log.error("Failed to start session", err));
    }

    public Mono<Void> stopSession(boolean logout) {
        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/api/sessions/{session}/stop")
                .queryParam("logout", logout)
                .build(config.sessionName()))
            .retrieve()
            .bodyToMono(Void.class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(v -> log.info("Session stopped (logout={})", logout))
            .doOnError(err -> log.error("Failed to stop session", err));
    }

    public Mono<WahaQRCodeResponse> getQRCode() {
        return webClient.get()
            .uri("/api/{session}/auth/qr", config.sessionName())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(WahaQRCodeResponse.class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(qr -> log.info("QR code retrieved successfully"))
            .doOnError(err -> log.error("Failed to get QR code", err));
    }

    public Mono<byte[]> getQRCodeImage() {
        return webClient.get()
            .uri("/api/{session}/auth/qr", config.sessionName())
            .accept(MediaType.IMAGE_PNG)
            .retrieve()
            .bodyToMono(byte[].class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(bytes -> log.info("QR code image retrieved ({} bytes)", bytes.length))
            .doOnError(err -> log.error("Failed to get QR code image", err));
    }

    private String formatChatId(String phoneNumber) {
        var cleanNumber = phoneNumber.replaceAll("[^0-9]", "");

        if (cleanNumber.length() < 10) {
            throw new IllegalArgumentException("Phone number too short: " + phoneNumber);
        }

        return cleanNumber + "@c.us";
    }

    public static class WahaClientException extends RuntimeException {
        public WahaClientException(String message) {
            super(message);
        }
    }

    public static class WahaServiceException extends RuntimeException {
        public WahaServiceException(String message) {
            super(message);
        }
    }
}