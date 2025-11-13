package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.receive.SetDefaultReceiveRequest;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.receive.DefaultReceiveResponse;
import com.bni.orange.wallet.service.command.ReceiveCommandService;
import com.bni.orange.wallet.service.query.ReceiveQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets")
public class ReceiveController {

  private final ReceiveCommandService command;
  private final ReceiveQueryService query;

  @PutMapping("/me/defaults/receive")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<DefaultReceiveResponse>> setDefaultReceiveWallet(
      @Valid @RequestBody SetDefaultReceiveRequest req
  ) {
    var dto = command.setDefaultReceiveWallet(req);
    return ResponseEntity.ok(ApiResponse.ok("Default receive wallet updated", dto));
  }

  @GetMapping("/me/defaults/receive")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<DefaultReceiveResponse>> getDefaultReceiveWallet() {
    var dto = query.getDefaultReceiveWallet();
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }
}
