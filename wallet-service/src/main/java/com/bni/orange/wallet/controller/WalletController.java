package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.request.*;
import com.bni.orange.wallet.model.response.*;
import com.bni.orange.wallet.service.IdempotencyService;
import com.bni.orange.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1")
@Validated
public class WalletController {

    private final WalletService service;
    private final IdempotencyService idemSvc;

    public WalletController(WalletService service, IdempotencyService idemSvc) {
        this.service = service; this.idemSvc = idemSvc;
    }

    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    @PostMapping("/wallets")
    public ResponseEntity<ApiResponse<WalletResponse>> create(
            @RequestHeader("Idempotency-Key") @NotBlank String idemKey,
            @RequestBody @Valid WalletCreateRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String xr,
            @RequestHeader(value = "X-Correlation-Id", required = false) String xc
    ) {
        final var scope = "wallet:create";
        var idem = idemSvc.beginOrReplay(scope, idemKey, req);
        if (!idem.fresh()) {
            @SuppressWarnings("unchecked")
            var replay = (ApiResponse<WalletResponse>) idem.replayBody();
            return ResponseEntity.status(idem.replayStatus()).body(replay);
        }
        var model = service.createWallet(req, idemKey, xr, xc);
        var body  = ApiResponse.created("Wallet created", model);
        idemSvc.complete(scope, idemKey, HttpStatus.CREATED.value(), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/v1/wallets/" + model.id())
                .body(body);
    }

    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    @GetMapping("/wallets/{wallet_id}")
    public ApiResponse<WalletResponse> getById(@PathVariable("wallet_id") UUID walletId) {
        return ApiResponse.ok("OK", service.getById(walletId));
    }
    
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    @PostMapping("/wallets/_lookup")
    public ApiResponse<WalletResponse> getByPhone(@RequestBody @Valid WalletGetByPhoneNumber req) {
        return ApiResponse.ok("OK", service.getByPhone(req.phone(), req.currency()));
    }

    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    @GetMapping("/wallets/{wallet_id}/balance")
    public ApiResponse<BalanceResponse> getBalance(@PathVariable("wallet_id") UUID walletId) {
        return ApiResponse.ok("OK", service.getBalance(walletId));
    }

    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    @PostMapping("/wallets/{wallet_id}/balance/adjust")
    public ResponseEntity<ApiResponse<BalanceResponse>> adjust(
            @RequestHeader("Idempotency-Key") @NotBlank String idemKey,
            @PathVariable("wallet_id") UUID walletId,
            @RequestBody @Valid BalanceAdjustRequest req,
            @RequestHeader(value = "X-Request-Id", required = false) String xr,
            @RequestHeader(value = "X-Correlation-Id", required = false) String xc
    ) {
        final var scope = "wallet:adjust:" + walletId;
        var idem = idemSvc.beginOrReplay(scope, idemKey, req);
        if (!idem.fresh()) {
            @SuppressWarnings("unchecked")
            var replay = (ApiResponse<BalanceResponse>) idem.replayBody();
            return ResponseEntity.status(idem.replayStatus()).body(replay);
        }
        var model = service.adjustBalance(walletId, req, idemKey, xr, xc);
        var body  = ApiResponse.ok("Balance adjusted", model);
        idemSvc.complete(scope, idemKey, HttpStatus.OK.value(), body);
        return ResponseEntity.ok(body);
    }
}
