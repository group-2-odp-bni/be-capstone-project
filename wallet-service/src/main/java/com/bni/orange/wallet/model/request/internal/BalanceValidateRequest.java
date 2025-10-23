package com.bni.orange.wallet.model.request.internal;

import com.bni.orange.wallet.model.enums.InternalAction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceValidateRequest(
    @NotNull UUID walletId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 20, fraction = 2) BigDecimal amount,
    @NotNull InternalAction action
) {}
