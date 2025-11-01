package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.receive.SetDefaultReceiveRequest;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.receive.DefaultReceiveResponse;
import com.bni.orange.wallet.service.command.ReceiveCommandService;
import com.bni.orange.wallet.service.query.ReceiveQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ReceiveController {

  private final ReceiveCommandService command;
  private final ReceiveQueryService query;

  public ReceiveController(ReceiveCommandService command, ReceiveQueryService query) {
    this.command = command;
    this.query = query;
  }

  @PutMapping("/wallets/receive/default")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<DefaultReceiveResponse>> setDefaultReceiveWallet(
      @Valid @RequestBody SetDefaultReceiveRequest req
  ) {
    var dto = command.setDefaultReceiveWallet(req);
    return ResponseEntity.ok(ApiResponse.ok("Default receive wallet updated", dto));
  }

  @GetMapping("/wallets/receive/default")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<DefaultReceiveResponse>> getDefaultReceiveWallet() {
    var dto = query.getDefaultReceiveWallet();
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }
}
