package com.bni.orange.transaction.controller;

import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.request.TopUpCallbackRequest;
import com.bni.orange.transaction.model.request.TopUpInitiateRequest;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.PaymentMethodResponse;
import com.bni.orange.transaction.model.response.TopUpInitiateResponse;
import com.bni.orange.transaction.model.response.VirtualAccountResponse;
import com.bni.orange.transaction.service.TopUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/topup")
public class TopUpController {

    private final TopUpService topUpService;

    private UUID getUserIdFromJwt(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> getPaymentMethods() {
        log.info("GET /api/v1/topup/payment-methods");
        var methods = topUpService.getPaymentMethods();
        return ResponseEntity.ok(ApiResponse.success(methods));
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TopUpInitiateResponse>> initiateTopUp(
        @Valid @RequestBody TopUpInitiateRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("POST /api/v1/topup/initiate - provider: {}, amount: {}", request.provider(), request.amount());
        var response = topUpService.initiateTopUp(request, getUserIdFromJwt(jwt));
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Top-up initiated successfully", response));
    }

    @GetMapping("/{transactionId}/status")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<VirtualAccountResponse>> getTopUpStatus(
        @PathVariable UUID transactionId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("GET /api/v1/topup/{}/status", transactionId);
        var response = topUpService.getTopUpStatus(transactionId, getUserIdFromJwt(jwt));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{transactionId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<Void>> cancelTopUp(
        @PathVariable UUID transactionId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        log.info("POST /api/v1/topup/{}/cancel", transactionId);
        topUpService.cancelTopUp(transactionId, getUserIdFromJwt(jwt));
        return ResponseEntity.ok(ApiResponse.success("Top-up cancelled successfully", null));
    }

    @PostMapping("/callback/{provider}")
    public ResponseEntity<ApiResponse<Void>> handleProviderCallback(
        @PathVariable String provider,
        @Valid @RequestBody TopUpCallbackRequest request,
        @RequestHeader(value = "X-Signature", required = false) String signature
    ) {
        log.info("POST /api/v1/topup/callback/{} - VA: {}", provider, request.vaNumber());

        var paymentProvider = provider.equalsIgnoreCase("bni")
            ? PaymentProvider.BNI_VA
            : PaymentProvider.valueOf(provider.toUpperCase());

        topUpService.processPaymentCallback(paymentProvider, request, signature);
        return ResponseEntity.ok(ApiResponse.success("Callback processed successfully", null));
    }
}
