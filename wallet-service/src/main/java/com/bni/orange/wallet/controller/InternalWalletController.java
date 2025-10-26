package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.internal.BalanceUpdateRequest;
import com.bni.orange.wallet.model.request.internal.BalanceValidateRequest;
import com.bni.orange.wallet.model.request.internal.RoleValidateRequest;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.internal.BalanceUpdateResponse;
import com.bni.orange.wallet.model.response.internal.RoleValidateResponse;
import com.bni.orange.wallet.model.response.internal.ValidationResultResponse;
import com.bni.orange.wallet.service.internal.InternalWalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
@Validated
public class InternalWalletController {

  private final InternalWalletService service;

  public InternalWalletController(InternalWalletService service) {
    this.service = service;
  }

  @PostMapping("/wallets/balance:validate")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<ValidationResultResponse>> validateBalance(
      @RequestBody @Valid BalanceValidateRequest req
  ) {
    var res = service.validateBalance(req);
    return ResponseEntity.ok(ApiResponse.ok(res.message(), res));
  }

  @PostMapping("/wallets/balance:update")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<BalanceUpdateResponse>> updateBalance(
      @RequestBody @Valid BalanceUpdateRequest req
  ) {
    var res = service.updateBalance(req);
    return ResponseEntity.ok(ApiResponse.ok(res.message(), res));
  }

  @PostMapping("/wallets/roles:validate")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<RoleValidateResponse>> validateRole(
      @RequestBody @Valid RoleValidateRequest req
  ) {
    var res = service.validateRole(req);
    return ResponseEntity.ok(ApiResponse.ok(res.message(), res));
  }
}
