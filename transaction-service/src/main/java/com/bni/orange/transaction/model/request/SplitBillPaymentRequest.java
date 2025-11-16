package com.bni.orange.transaction.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SplitBillPaymentRequest(
    @NotBlank String billId,
    @NotBlank String memberId,
    @NotBlank String billTitle,
    @NotNull UUID billOwnerUserId,
    @NotNull UUID sourceWalletId,
    @NotNull UUID destinationWalletId,
    @NotNull BigDecimal amount
) {}
