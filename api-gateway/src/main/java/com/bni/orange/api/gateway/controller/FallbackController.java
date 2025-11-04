package com.bni.orange.api.gateway.controller;

import com.bni.orange.api.gateway.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @PostMapping("/auth")
    @GetMapping("/auth")
    public Mono<ResponseEntity<ApiResponse<Void>>> authServiceFallback() {
        return Mono.just(createFallbackResponse("Authentication", "/fallback/auth"));
    }

    @PostMapping("/user")
    @GetMapping("/user")
    public Mono<ResponseEntity<ApiResponse<Void>>> userServiceFallback() {
        return Mono.just(createFallbackResponse("User", "/fallback/user"));
    }

    @PostMapping("/wallet")
    @GetMapping("/wallet")
    public Mono<ResponseEntity<ApiResponse<Void>>> walletServiceFallback() {
        return Mono.just(createFallbackResponse("Wallet", "/fallback/wallet"));
    }

    @PostMapping("/transaction")
    @GetMapping("/transaction")
    public Mono<ResponseEntity<ApiResponse<Void>>> transactionServiceFallback() {
        return Mono.just(createFallbackResponse("Transaction", "/fallback/transaction"));
    }

    private ResponseEntity<ApiResponse<Void>> createFallbackResponse(String serviceName, String path) {
        ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
            .message("Service Unavailable")
            .error(ApiResponse.ErrorDetail.builder()
                .code("SERVICE_UNAVAILABLE")
                .message(String.format("The %s service is temporarily unavailable. Please try again later.", serviceName))
                .build())
            .timestamp(Instant.now())
            .path(path)
            .build();

        return new ResponseEntity<>(responseBody, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
