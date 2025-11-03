package com.bni.orange.transaction.controller;

import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.PageResponse;
import com.bni.orange.transaction.model.response.TransactionResponse;
import com.bni.orange.transaction.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
public class TransactionHistoryController {

    private final TransactionHistoryService historyService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getUserTransactions(
        @RequestParam(required = false) UUID walletId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction,
        @RequestParam(required = false) TransactionStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var pageResult = historyService.getUserTransactions(
            getUserIdFromJwt(jwt), walletId, status, startDate, endDate,
            PageRequest.of(page, size, Sort.by(direction, sortBy))
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(pageResult)));
    }

    @GetMapping("/all-wallets")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getAllWalletTransactions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") Sort.Direction direction,
        @RequestParam(required = false) TransactionStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var pageResult = historyService.getAllWalletTransactions(
            getUserIdFromJwt(jwt), status, startDate, endDate,
            PageRequest.of(page, size, Sort.by(direction, sortBy))
        );
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(pageResult)));
    }

    @GetMapping("/{transactionId}")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionDetail(
        @PathVariable UUID transactionId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getTransactionDetail(transactionId, getUserIdFromJwt(jwt))));
    }

    @GetMapping("/ref/{transactionRef}")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionByRef(
        @PathVariable String transactionRef,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(historyService.getTransactionByRef(transactionRef, getUserIdFromJwt(jwt))));
    }

    private UUID getUserIdFromJwt(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
