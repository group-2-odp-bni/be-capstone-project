package com.bni.orange.notification.controller;

import com.bni.orange.notification.dto.WahaQRCodeResponse;
import com.bni.orange.notification.dto.WahaSessionResponse;
import com.bni.orange.notification.service.WahaSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/whatsapp/session")
public class WahaSessionController {

    private final WahaSessionService sessionService;

    @GetMapping("/status")
    public Mono<ResponseEntity<WahaSessionResponse>> getStatus() {
        return sessionService.getSessionStatus()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    @PostMapping("/start")
    public Mono<ResponseEntity<String>> startSession() {
        return sessionService.startSession()
            .then(Mono.just(ResponseEntity.ok("Session start initiated")))
            .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to start session"));
    }

    @PostMapping("/stop")
    public Mono<ResponseEntity<String>> stopSession(
        @RequestParam(defaultValue = "false") boolean logout) {
        return sessionService.stopSession(logout)
            .then(Mono.just(ResponseEntity.ok("Session stopped (logout=" + logout + ")")))
            .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to stop session"));
    }

    @GetMapping("/qr")
    public Mono<ResponseEntity<WahaQRCodeResponse>> getQRCode() {
        return sessionService.getQRCode()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build());
    }

    @GetMapping(value = "/qr/image", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> getQRCodeImage() {
        return sessionService.getQRCodeImage()
            .map(bytes -> ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(bytes))
            .onErrorReturn(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .build());
    }

    @GetMapping("/ready")
    public Mono<ResponseEntity<Boolean>> isReady() {
        return sessionService.isSessionReady()
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.ok(false));
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return sessionService.isSessionReady()
            .map(ready -> ready
                ? ResponseEntity.ok("WhatsApp session is READY")
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("WhatsApp session is NOT READY")
            )
            .onErrorResume(error ->
                Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("WhatsApp session health check failed: " + error.getMessage()))
            );
    }
}
