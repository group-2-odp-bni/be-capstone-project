package com.bni.orange.notification.service;

import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.model.response.WahaQRCodeResponse;
import com.bni.orange.notification.model.response.WahaSessionResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static reactor.util.retry.Retry.backoff;

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

    @Retry(name = "waha-session", fallbackMethod = "startSessionFallback")
    @CircuitBreaker(name = "waha-session")
    public Mono<Void> startSession() {
        log.info("🚀 Starting WhatsApp session process...");
        return getSessionStatus()
            .flatMap(session -> {
                String status = session.status();
                if ("WORKING".equalsIgnoreCase(status)) {
                    log.info("✅ Session is already WORKING.");
                    return Mono.empty();
                }
                log.warn("⚠️ Session status is '{}', not 'WORKING'. Attempting to stop and restart.", status);
                return wahaApiClient.stopSession(true)
                    .then(wahaApiClient.startSession());
            })
            .onErrorResume(e -> {
                log.warn("⚠️ Could not get or check session status ({}). Proceeding with a fresh start attempt.", e.getMessage());
                return wahaApiClient.startSession();
            })
            .doOnSuccess(v -> log.info("✅ Session start request processed successfully."))
            .then();
    }

    @SuppressWarnings("unused")
    private Mono<Void> startSessionFallback(Exception e) {
        log.error("❌ All retry attempts failed to start WAHA session. Health monitor will retry later.", e);
        return Mono.empty();
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
                    log.info("Session is ready! {}",session);
                    return Mono.just(session);
                }
                if ("FAILED".equalsIgnoreCase(session.status())) {
                    log.error("Session failed! Manual intervention required.");
                    return Mono.error(new IllegalStateException("Session is in FAILED state"));
                }
                log.info("Session status: {}. Waiting...", session.status());
                return Mono.error(new SessionNotReadyException(session.status()));
            })
            .retryWhen(backoff(maxAttempts, Duration.ofSeconds(delaySeconds))
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
