package com.bni.orange.transaction.controller;

import com.bni.orange.transaction.model.request.InternalTransferRequest;
import com.bni.orange.transaction.model.request.RecipientLookupRequest;
import com.bni.orange.transaction.model.request.TransferConfirmRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.RecipientLookupResponse;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    private UUID getUserIdFromJwt(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @PostMapping("/inquiry")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<RecipientLookupResponse>> inquiryRecipient(
        @Valid @RequestBody RecipientLookupRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var recipient = transferService.inquiry(request, getUserIdFromJwt(jwt), jwt.getTokenValue());
        return ResponseEntity.ok(ApiResponse.success(recipient));
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateTransfer(
        @Valid @RequestBody TransferInitiateRequest request,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var transaction = transferService.initiateTransfer(request, getUserIdFromJwt(jwt), idempotencyKey, jwt.getTokenValue());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Transfer initiated successfully", transaction));
    }

    @PostMapping("/{transactionId}/execute")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TransactionResponse>> executeTransfer(
        @PathVariable UUID transactionId,
        @Valid @RequestBody TransferConfirmRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var transaction = transferService.confirmTransfer(
            transactionId, request, getUserIdFromJwt(jwt), jwt.getTokenValue()
        );
        return ResponseEntity.ok(ApiResponse.success("Transfer executed successfully", transaction));
    }

    @PostMapping("/internal")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateInternalTransfer(
        @Valid @RequestBody InternalTransferRequest request,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var transaction = transferService.initiateInternalTransfer(request, getUserIdFromJwt(jwt), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Internal transfer executed successfully", transaction));
    }
}
