package com.bni.orange.notification.service;

import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.model.response.WahaQRCodeResponse;
import com.bni.orange.notification.model.response.WahaSessionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class WahaSessionService {

    private final WahaApiClient wahaApiClient;

    public Mono<WahaSessionResponse> getSessionStatus() {
        log.debug("Fetching session status");
        return wahaApiClient.getSessionStatus()
            .doOnSuccess(session ->
                log.info("Session status: {}", session.status())
            );
    }

    public Mono<Void> startSession() {
        log.info("Starting WhatsApp session");
        return wahaApiClient.startSession()
            .doOnSuccess(v ->
                log.info("Session start request successful")
            );
    }

    public Mono<Void> stopSession(boolean logout) {
        log.info("Stopping WhatsApp session (logout={})", logout);
        return wahaApiClient.stopSession(logout)
            .doOnSuccess(v ->
                log.info("Session stopped successfully")
            );
    }


    public Mono<WahaQRCodeResponse> getQRCode() {
        log.debug("Fetching QR code");
        return wahaApiClient.getQRCode()
            .doOnSuccess(qr ->
                log.info("QR code retrieved successfully")
            );
    }

    public Mono<byte[]> getQRCodeImage() {
        log.debug("Fetching QR code image");
        return wahaApiClient.getQRCodeImage()
            .doOnSuccess(bytes ->
                log.info("QR code image retrieved ({} bytes)", bytes.length)
            );
    }

    public Mono<Boolean> isSessionReady() {
        return getSessionStatus()
            .map(session -> "WORKING".equalsIgnoreCase(session.status()))
            .onErrorReturn(false)
            .timeout(Duration.ofSeconds(5))
            .doOnNext(ready ->
                log.info("Session ready status: {}", ready)
            );
    }

    public Mono<WahaSessionResponse> waitForSessionReady(int maxAttempts, int delaySeconds) {
        return getSessionStatus()
            .flatMap(session -> {
                if ("WORKING".equalsIgnoreCase(session.status())) {
                    log.info("Session is ready!");
                    return Mono.just(session);
                }
                if ("FAILED".equalsIgnoreCase(session.status())) {
                    log.error("Session failed! Manual intervention required.");
                    return Mono.error(new IllegalStateException("Session is in FAILED state"));
                }
                log.info("Session status: {}. Waiting...", session.status());
                return Mono.error(new SessionNotReadyException(session.status()));
            })
            .retryWhen(Retry.backoff(maxAttempts, Duration.ofSeconds(delaySeconds))
                .filter(throwable -> throwable instanceof SessionNotReadyException)
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                    new IllegalStateException("Session did not become ready after " + maxAttempts + " attempts.", retrySignal.failure())
                )
            );
    }

    public static class SessionNotReadyException extends RuntimeException {
        public SessionNotReadyException(String status) {
            super("Session not ready. Current status: " + status);
        }
    }
}
