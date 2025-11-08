package com.bni.orange.wallet.model.request.internal;

import com.bni.orange.wallet.model.enums.InternalAction;
import com.bni.orange.wallet.model.enums.TransferType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RoleValidateRequest(
    @NotNull UUID walletId,
    @NotNull UUID userId,
    @NotNull InternalAction action,
    BigDecimal amount,
    TransferType transferType
) {}
