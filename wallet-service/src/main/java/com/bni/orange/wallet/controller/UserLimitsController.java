package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.limits.UserLimitsUpdateRequest;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.model.response.limits.UserLimitsResponse;
import com.bni.orange.wallet.service.command.LimitsCommandService;
import com.bni.orange.wallet.service.query.LimitsQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class UserLimitsController {

  private final LimitsCommandService command;
  private final LimitsQueryService query;

  public UserLimitsController(LimitsCommandService command, LimitsQueryService query) {
    this.command = command; this.query = query;
  }

  @GetMapping("/wallets/limits")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<UserLimitsResponse>> getMyLimits() {
    var dto = query.getMyLimits();
    return ResponseEntity.ok(ApiResponse.ok("OK", dto));
  }

  @PutMapping("/wallets/limits")
  @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
  public ResponseEntity<ApiResponse<UserLimitsResponse>> updateMyLimits(
      @Valid @RequestBody UserLimitsUpdateRequest req
  ) {
    var dto = command.updateMyLimits(req);
    return ResponseEntity.ok(ApiResponse.ok("User limits updated", dto));
  }
}
