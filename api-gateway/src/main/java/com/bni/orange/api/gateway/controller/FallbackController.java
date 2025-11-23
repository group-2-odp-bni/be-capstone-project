package com.bni.orange.api.gateway.controller;

import com.bni.orange.api.gateway.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(value = "/auth", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<ApiResponse<Void>>> authServiceFallback() {
        return Mono.just(createFallbackResponse("Authentication", "/fallback/auth"));
    }

    @RequestMapping(value = "/user", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<ApiResponse<Void>>> userServiceFallback() {
        return Mono.just(createFallbackResponse("User", "/fallback/user"));
    }

    @RequestMapping(value = "/wallet", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<ApiResponse<Void>>> walletServiceFallback() {
        return Mono.just(createFallbackResponse("Wallet", "/fallback/wallet"));
    }

    @RequestMapping(value = "/transaction", method = {RequestMethod.GET, RequestMethod.POST})
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
