package com.bni.orange.transaction.model.request.internal;

import com.bni.orange.transaction.model.enums.InternalAction;
import com.bni.orange.transaction.model.enums.TransferType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record RoleValidateRequest(
    @NotNull
    UUID walletId,

    @NotNull
    UUID userId,

    @NotNull
    InternalAction action,

    @Digits(integer = 20, fraction = 2)
    BigDecimal amount,

    TransferType transferType
) {}
