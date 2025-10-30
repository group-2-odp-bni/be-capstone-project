package com.bni.orange.notification.client;

import com.bni.orange.notification.config.properties.WahaConfigProperties;
import com.bni.orange.notification.model.response.WahaMessageResponse;
import com.bni.orange.notification.model.response.WahaQRCodeResponse;
import com.bni.orange.notification.model.response.WahaSessionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class WahaApiClient {

    private static final String API_SEND_TEXT = "/api/sendText";
    private static final String API_SESSIONS_TEMPLATE = "/api/sessions/{session}";
    private static final String API_SESSIONS_START_TEMPLATE = "/api/sessions/{session}/start";
    private static final String API_SESSIONS_STOP_TEMPLATE = "/api/sessions/{session}/stop";
    private static final String API_AUTH_QR_TEMPLATE = "/api/{session}/auth/qr";

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
        var context = "send message to " + phoneNumber;

        log.info("Sending WhatsApp message to {} via WAHA", phoneNumber);

        return webClient.post()
            .uri(API_SEND_TEXT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, handleClientError(context))
            .onStatus(HttpStatusCode::is5xxServerError, handleServerError(context))
            .bodyToMono(WahaMessageResponse.class)
            .timeout(Duration.ofSeconds(30))
            .transformDeferred(RetryOperator.of(retry))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .doOnSuccess(response ->
                log.info("Successfully sent WhatsApp message to {}. Message ID: {}", phoneNumber, response.id())
            )
            .doOnError(err -> {
                if (!(err instanceof WahaClientException || err instanceof WahaServiceException)) {
                    log.error("Failed to send WhatsApp message to {} after all retries: {}", phoneNumber, err.getMessage());
                }
            });
    }

    public Mono<WahaSessionResponse> getSessionStatus() {
        return webClient.get()
            .uri(API_SESSIONS_TEMPLATE, config.sessionName())
            .retrieve()
            .bodyToMono(WahaSessionResponse.class)
            .timeout(Duration.ofSeconds(10))
            .doOnSuccess(session -> log.info("Session status: {}", session.status()))
            .doOnError(err -> log.error("Failed to get session status", err));
    }

    public Mono<Void> startSession() {
        return webClient.post()
            .uri(API_SESSIONS_START_TEMPLATE, config.sessionName())
            .retrieve()
            .bodyToMono(Void.class)
            .timeout(Duration.ofSeconds(45))
            .doOnSuccess(v -> log.info("Session start request acknowledged."))
            .doOnError(err -> log.error("Failed to start session", err));
    }

    public Mono<Void> stopSession(boolean logout) {
        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path(API_SESSIONS_STOP_TEMPLATE)
                .queryParam("logout", logout)
                .build(config.sessionName()))
            .retrieve()
            .bodyToMono(Void.class)
            .timeout(Duration.ofSeconds(30))
            .doOnSuccess(v -> log.info("Session stopped (logout={})", logout))
            .doOnError(err -> log.error("Failed to stop session", err));
    }

    public Mono<WahaQRCodeResponse> getQRCode() {
        return webClient.get()
            .uri(API_AUTH_QR_TEMPLATE, config.sessionName())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono(WahaQRCodeResponse.class)
            .timeout(Duration.ofSeconds(30))
            .doOnSuccess(qr -> log.info("QR code retrieved successfully"))
            .doOnError(err -> log.error("Failed to get QR code", err));
    }

    public Mono<byte[]> getQRCodeImage() {
        return webClient.get()
            .uri(API_AUTH_QR_TEMPLATE, config.sessionName())
            .accept(MediaType.IMAGE_PNG)
            .retrieve()
            .bodyToMono(byte[].class)
            .timeout(Duration.ofSeconds(30))
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

    private Function<ClientResponse, Mono<? extends Throwable>> handleClientError(String context) {
        return response -> response.bodyToMono(String.class)
            .switchIfEmpty(Mono.just("[no body]"))
            .flatMap(body -> {
                log.error("Client error for {}: {}", context, body);
                return Mono.error(new WahaClientException("Client error: " + body));
            });
    }

    private Function<ClientResponse, Mono<? extends Throwable>> handleServerError(String context) {
        return response -> response.bodyToMono(String.class)
            .switchIfEmpty(Mono.just("[no body]"))
            .flatMap(body -> {
                log.error("Server error for {}: {}", context, body);
                return Mono.error(new WahaServiceException("WAHA service error: " + body));
            });
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