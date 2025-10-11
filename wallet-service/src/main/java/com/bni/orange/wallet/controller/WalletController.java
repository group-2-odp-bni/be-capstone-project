package com.bni.orange.wallet.controller;

import com.bni.orange.wallet.model.response.BalanceResponse;
import com.bni.orange.wallet.model.response.WalletResponse;
import com.bni.orange.wallet.service.WalletService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/wallets")
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(
            @RequestParam("user_id") @NotNull UUID userId,
            @RequestParam(value = "currency", defaultValue = "IDR") @NotBlank String currency
    ) {
        return service.createWallet(userId, currency.toUpperCase());
    }

    @GetMapping("/wallets/{wallet_id}")
    public WalletResponse getById(@PathVariable("wallet_id") UUID walletId) {
        return service.getById(walletId);
    }

    @GetMapping("/wallets/by-user")
    public WalletResponse getByUser(
            @RequestParam("user_id") UUID userId,
            @RequestParam(value = "currency", defaultValue = "IDR") String currency
    ) {
        return service.getByUser(userId, currency.toUpperCase());
    }

    @GetMapping("/wallets/{wallet_id}/balance")
    public BalanceResponse getBalance(@PathVariable("wallet_id") UUID walletId) {
        return service.getBalance(walletId);
    }
}
