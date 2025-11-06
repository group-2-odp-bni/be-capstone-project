package com.bni.orange.notification.controller;

import com.bni.orange.notification.component.WahaSessionHealthMonitor;
import com.bni.orange.notification.service.WahaSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/actuator/whatsapp")
@RequiredArgsConstructor
public class WahaHealthController {

    private final WahaSessionService sessionService;
    private final WahaSessionHealthMonitor healthMonitor;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> getHealth() {
        return sessionService.getSessionStatus()
            .map(status -> {
                boolean isHealthy = "WORKING".equalsIgnoreCase(status.status());
                HttpStatus httpStatus = isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

                var metrics = healthMonitor.getMetrics();

                return ResponseEntity
                    .status(httpStatus)
                    .body(Map.of(
                        "status", status.status(),
                        "healthy", isHealthy,
                        "timestamp", Instant.now(),
                        "metrics", Map.of(
                            "consecutiveFailures", metrics.consecutiveFailures(),
                            "totalRecoveryAttempts", metrics.totalRecoveryAttempts()
                        )
                    ));
            })
            .onErrorResume(error -> {
                log.error("Failed to check WAHA health", error);
                var metrics = healthMonitor.getMetrics();
                return Mono.just(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                            "status", "ERROR",
                            "healthy", false,
                            "timestamp", Instant.now(),
                            "error", error.getMessage(),
                            "metrics", Map.of(
                                "consecutiveFailures", metrics.consecutiveFailures(),
                                "totalRecoveryAttempts", metrics.totalRecoveryAttempts()
                            )
                        ))
                );
            });
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> getStatus() {
        return sessionService.getSessionStatus()
            .map(status -> ResponseEntity.ok(Map.of(
                "session", status,
                "timestamp", Instant.now()
            )))
            .onErrorResume(error ->
                Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                        "error", error.getMessage(),
                        "timestamp", Instant.now()
                    )))
            );
    }

    @GetMapping("/ready")
    public Mono<ResponseEntity<Map<String, Object>>> isReady() {
        return sessionService.isSessionReady()
            .map(ready -> {
                Map<String, Object> response = new HashMap<>();
                response.put("ready", ready);
                response.put("timestamp", Instant.now());
                return ResponseEntity.ok(response);
            })
            .onErrorResume(error -> {
                Map<String, Object> response = new HashMap<>();
                response.put("ready", false);
                response.put("timestamp", Instant.now());
                return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
            });
    }
}
