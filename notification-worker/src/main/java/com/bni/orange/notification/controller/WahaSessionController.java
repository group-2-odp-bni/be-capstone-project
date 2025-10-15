package com.bni.orange.notification.controller;

import com.bni.orange.notification.model.response.WahaQRCodeResponse;
import com.bni.orange.notification.model.response.WahaSessionResponse;
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
    public Mono<WahaSessionResponse> getStatus() {
        return sessionService.getSessionStatus();
    }

    @PostMapping("/start")
    public Mono<ResponseEntity<String>> startSession() {
        return sessionService.startSession()
            .then(Mono.just(ResponseEntity.ok("Session start initiated")));
    }

    @PostMapping("/stop")
    public Mono<ResponseEntity<String>> stopSession(
        @RequestParam(defaultValue = "false") boolean logout
    ) {
        return sessionService.stopSession(logout)
            .then(Mono.just(ResponseEntity.ok("Session stopped (logout=" + logout + ")")));
    }

    @GetMapping("/qr")
    public Mono<WahaQRCodeResponse> getQRCode() {
        return sessionService.getQRCode();
    }

    @GetMapping(value = "/qr/image", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<byte[]> getQRCodeImage() {
        return sessionService.getQRCodeImage();
    }

    @GetMapping("/ready")
    public Mono<Boolean> isReady() {
        return sessionService.isSessionReady();
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return sessionService.isSessionReady()
            .map(ready -> ready
                ? ResponseEntity.ok("WhatsApp session is READY")
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("WhatsApp session is NOT READY")
            );
    }
}
