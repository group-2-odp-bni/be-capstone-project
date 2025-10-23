package com.bni.orange.wallet.model.request.internal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceUpdateRequest(
    @NotNull UUID walletId,
    @NotNull @Digits(integer = 20, fraction = 2) BigDecimal delta,
    @NotBlank String referenceId,
    String reason
) {}
