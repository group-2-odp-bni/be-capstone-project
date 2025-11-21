package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.policy.WalletPolicyResponse;
import com.bni.orange.wallet.service.query.WalletPolicyQueryService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class WalletPolicyController {

  private final WalletPolicyQueryService query;

  public WalletPolicyController(WalletPolicyQueryService query) {
    this.query = query;
  }

  @GetMapping("/wallets/{walletId}/policy")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<WalletPolicyResponse>> getPolicy(
      @PathVariable @NotNull UUID walletId
  ) {
    var dto = query.getWalletPolicy(walletId);
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }
}
