package com.bni.orange.notification.controller.advice;

import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.service.WahaSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalControllerAdvice {

    @ExceptionHandler(WahaApiClient.WahaClientException.class)
    public ResponseEntity<Map<String, String>> handleWahaClientException(WahaApiClient.WahaClientException ex) {
        log.warn("Handling WAHA client exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", "Upstream client error", "message", ex.getMessage()));
    }

    @ExceptionHandler(WahaApiClient.WahaServiceException.class)
    public ResponseEntity<Map<String, String>> handleWahaServiceException(WahaApiClient.WahaServiceException ex) {
        log.error("Handling WAHA service exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Upstream service unavailable", "message", ex.getMessage()));
    }

    @ExceptionHandler(WahaSessionService.SessionNotReadyException.class)
    public ResponseEntity<Map<String, String>> handleSessionNotReadyException(WahaSessionService.SessionNotReadyException ex) {
        log.warn("Handling session not ready exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Session not ready", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        log.error("Handling illegal state exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "Conflict or illegal state", "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Handling unexpected exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "An internal error occurred", "message", ex.getMessage()));
    }
}
