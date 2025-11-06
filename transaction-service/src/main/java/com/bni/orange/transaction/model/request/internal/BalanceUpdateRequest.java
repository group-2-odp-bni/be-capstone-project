package com.bni.orange.transaction.model.request.internal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record BalanceUpdateRequest(
    @NotNull
    UUID walletId,

    @NotNull
    @Digits(integer = 20, fraction = 2)
    BigDecimal delta,

    @NotBlank
    String referenceId,

    String reason,

    @NotNull
    UUID actorUserId
) {}
