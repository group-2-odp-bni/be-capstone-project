package com.bni.orange.transaction.controller;

import com.bni.orange.transaction.model.request.SplitBillPaymentRequest;
import com.bni.orange.transaction.model.request.TransferConfirmRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.response.ApiResponse;
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
@RequestMapping("/api/v1/split-bill-payments")
public class SplitBillPaymentController {

    private final TransferService transferService;

    @PostMapping("/initiate")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateSplitBillPayment(
        @Valid @RequestBody SplitBillPaymentRequest request,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var userId = UUID.fromString(jwt.getSubject());

        var transferRequest = TransferInitiateRequest.builder()
            .receiverUserId(request.billOwnerUserId())
            .receiverWalletId(request.destinationWalletId())
            .senderWalletId(request.sourceWalletId())
            .amount(request.amount())
            .notes("Split Bill Payment - " + request.billTitle())
            .splitBillId(request.billId())
            .splitBillMemberId(request.memberId())
            .currency("IDR")
            .build();

        var transaction = transferService.initiateTransfer(transferRequest, userId, idempotencyKey, jwt.getTokenValue());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Split Bill payment initiated", transaction));
    }

    @PostMapping("/{transactionId}/execute")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TransactionResponse>> executeSplitBillPayment(
        @PathVariable UUID transactionId,
        @Valid @RequestBody TransferConfirmRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var userId = UUID.fromString(jwt.getSubject());

        var transaction = transferService.confirmTransfer(
            transactionId, request, userId, jwt.getTokenValue()
        );

        return ResponseEntity.ok().body(ApiResponse.success("Split Bill payment completed", transaction));
    }
}
