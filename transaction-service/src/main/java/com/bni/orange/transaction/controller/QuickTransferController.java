package com.bni.orange.transaction.controller;

import com.bni.orange.transaction.model.request.QuickTransferAddRequest;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.QuickTransferResponse;
import com.bni.orange.transaction.service.QuickTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/quick-transfers")
public class QuickTransferController {

    private final QuickTransferService quickTransferService;

    private UUID getUserIdFromJwt(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<List<QuickTransferResponse>>> getQuickTransfers(
        @RequestParam UUID walletId,
        @RequestParam(defaultValue = "usage") String orderBy,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            quickTransferService.getWalletQuickTransfers(getUserIdFromJwt(jwt), walletId, orderBy)));
    }

    @GetMapping("/top")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<List<QuickTransferResponse>>> getTopQuickTransfers(
        @RequestParam UUID walletId,
        @RequestParam(defaultValue = "8") int limit,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            quickTransferService.getTopWalletQuickTransfers(getUserIdFromJwt(jwt), walletId, limit)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<QuickTransferResponse>> addQuickTransfer(
        @Valid @RequestBody QuickTransferAddRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Quick transfer added successfully", quickTransferService.addQuickTransfer(getUserIdFromJwt(jwt), request)));
    }

    @DeleteMapping("/{recipientUserId}")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<Void>> removeQuickTransfer(
        @PathVariable UUID recipientUserId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        quickTransferService.removeQuickTransfer(getUserIdFromJwt(jwt), recipientUserId);
        return ResponseEntity.ok(ApiResponse.success("Quick transfer removed successfully", null));
    }

    @PutMapping("/{quickTransferId}/order")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<Void>> updateDisplayOrder(
        @PathVariable UUID quickTransferId,
        @RequestParam int order,
        @AuthenticationPrincipal Jwt jwt
    ) {
        quickTransferService.updateDisplayOrder(getUserIdFromJwt(jwt), quickTransferId, order);
        return ResponseEntity.ok(ApiResponse.success("Display order updated successfully", null));
    }
}
