package com.bni.orange.transaction.model.request.internal;

import com.bni.orange.transaction.model.enums.InternalAction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record BalanceValidateRequest(
    @NotNull
    UUID walletId,

    @NotNull
    @Digits(integer = 20, fraction = 2)
    BigDecimal delta,

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 20, fraction = 2)
    BigDecimal amount,

    @NotNull
    InternalAction action,

    @NotNull
    UUID actorUserId
) {}
