package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.wallet.WalletCreateRequest;
import com.bni.orange.wallet.model.request.wallet.WalletUpdateRequest;
import com.bni.orange.wallet.model.response.*;
import com.bni.orange.wallet.model.response.wallet.WalletDeleteResultResponse;
import com.bni.orange.wallet.service.command.WalletCommandService;
import com.bni.orange.wallet.service.query.WalletQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class WalletController {

  private final WalletCommandService command;
  private final WalletQueryService query;

  public WalletController(WalletCommandService command, WalletQueryService query) {
    this.command = command; this.query = query;
  }

  @PostMapping("/wallets")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletDetailResponse>> createWallet(
      @RequestHeader(value="Idempotency-Key", required=false) String idemKey,
      @Valid @RequestBody WalletCreateRequest req
  ) {
    var dto = command.createWallet(req, idemKey);
    return ResponseEntity
        .created(URI.create("/api/v1/wallets/" + dto.getId()))
        .body(ApiResponse.ok("Wallet created", dto));
  }

  @GetMapping("/wallets")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<List<WalletListItemResponse>>> listMyWallets(
      @RequestParam(defaultValue="0") @Min(0) int page,
      @RequestParam(defaultValue="20") @Min(1) int size
  ) {
    var list = query.listMyWallets(page, size);
    return ResponseEntity.ok(ApiResponse.ok("OK", list));
  }

  @GetMapping("/wallets/{walletId}")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletDetailResponse>> getWalletDetail(@PathVariable UUID walletId) {
    var dto = query.getWalletDetail(walletId);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }

  @PatchMapping("/wallets/{walletId}")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletDetailResponse>> updateWallet(
      @PathVariable UUID walletId,
      @Valid @RequestBody WalletUpdateRequest req
  ) {
    var dto = command.updateWallet(walletId, req);
    return ResponseEntity.ok(ApiResponse.ok("Wallet updated", dto));
  }

  @GetMapping("/wallets/{walletId}/balance")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(@PathVariable UUID walletId) {
    var dto = query.getBalance(walletId);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }
  @DeleteMapping("/wallets/{walletId}")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletDeleteResultResponse>> deleteWallet(
      @PathVariable UUID walletId
  ) {
    var dto = command.deleteWallet(walletId);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }

  @PostMapping("/wallets/delete/confirm")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletDeleteResultResponse>> confirmDeleteWallet(
      @RequestParam("token") String token
  ) {
    var dto = command.confirmDeleteWallet(token);
    return ResponseEntity.ok(ApiResponse.ok("Wallet deleted", dto));
  }
  @PostMapping("/wallets/delete/approve")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletDeleteResultResponse>> approveDeleteWallet(
      @RequestParam("token") String token
  ) {
    var dto = command.approveDeleteWallet(token);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }

}

