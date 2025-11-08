package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.wallet.model.request.internal.BalanceValidateRequest;
import com.bni.orange.wallet.model.request.internal.RoleValidateRequest;
import com.bni.orange.wallet.model.request.internal.ValidateWalletOwnershipRequest;
import com.bni.orange.wallet.model.request.wallet.WalletCreateRequest;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.WalletDetailResponse;
import com.bni.orange.wallet.model.response.internal.BalanceUpdateResponse;
import com.bni.orange.wallet.model.response.internal.DefaultWalletResponse;
import com.bni.orange.wallet.model.response.internal.RoleValidateResponse;
import com.bni.orange.wallet.model.response.internal.UserWalletsResponse;
import com.bni.orange.wallet.model.response.internal.ValidateWalletOwnershipResponse;
import com.bni.orange.wallet.model.response.internal.ValidationResultResponse;
import com.bni.orange.wallet.service.command.WalletCommandService;
import com.bni.orange.wallet.service.internal.InternalWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalWalletController {

  private final InternalWalletService service;
  private final WalletCommandService command;

  @GetMapping("/users/{userId}/wallets")
  public ResponseEntity<ApiResponse<UserWalletsResponse>> getUserWallets(
      @PathVariable UUID userId,
      @RequestParam(defaultValue = "true") boolean idsOnly
  ) {
    var res = service.getWalletsByUserId(userId, idsOnly);
    return ResponseEntity.ok(ApiResponse.ok("OK", res));
  }

  @PostMapping("/wallets/balance:validate")
  public ResponseEntity<ApiResponse<ValidationResultResponse>> validateBalance(
      @RequestBody @Valid BalanceValidateRequest req
  ) {
    var res = service.validateBalance(req);
    return ResponseEntity.ok(ApiResponse.ok(res.message(), res));
  }

  @PostMapping("/wallets/balance:update")
  public ResponseEntity<ApiResponse<BalanceUpdateResponse>> updateBalance(
      @RequestBody @Valid BalanceUpdateRequest req
  ) {
    var res = service.updateBalance(req);
    return ResponseEntity.ok(ApiResponse.ok(res.message(), res));
  }

  @PostMapping("/wallets/roles:validate")
  public ResponseEntity<ApiResponse<RoleValidateResponse>> validateRole(
      @RequestBody @Valid RoleValidateRequest req
  ) {
    var res = service.validateRole(req);
    return ResponseEntity.ok(ApiResponse.ok(res.message(), res));
  }

  @GetMapping("/users/{userId}/default-wallet")
  public ResponseEntity<ApiResponse<DefaultWalletResponse>> getDefaultWalletByUserId(
      @PathVariable UUID userId
  ) {
    var res = service.getDefaultWalletByUserId(userId);
    return ResponseEntity.ok(ApiResponse.ok("OK", res));
  }
  @PostMapping("/wallets")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletDetailResponse>> createWallet(
      @RequestHeader(value="Idempotency-Key", required=false) String idemKey,
      @Valid @RequestBody WalletCreateRequest req
  ) {
    var dto = command.createWallet(req, idemKey);
    return ResponseEntity
        .created(URI.create("/internal/v1/wallets/" + dto.getId()))
        .body(ApiResponse.ok("Wallet created", dto));
  }

  @PostMapping("/wallets/ownership:validate")
  public ResponseEntity<ApiResponse<ValidateWalletOwnershipResponse>> validateWalletOwnership(
      @RequestBody @Valid ValidateWalletOwnershipRequest req
  ) {
    var res = service.validateWalletOwnership(req);
    return ResponseEntity.ok(ApiResponse.ok("OK", res));
  }
}
