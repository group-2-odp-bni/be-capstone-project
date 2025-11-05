package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.wallet.model.request.internal.BalanceValidateRequest;
import com.bni.orange.wallet.model.request.internal.RoleValidateRequest;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.internal.BalanceUpdateResponse;
import com.bni.orange.wallet.model.response.internal.DefaultWalletResponse;
import com.bni.orange.wallet.model.response.internal.RoleValidateResponse;
import com.bni.orange.wallet.model.response.internal.UserWalletsResponse;
import com.bni.orange.wallet.model.response.internal.ValidationResultResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalWalletController {

  private final InternalWalletService service;

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
}
