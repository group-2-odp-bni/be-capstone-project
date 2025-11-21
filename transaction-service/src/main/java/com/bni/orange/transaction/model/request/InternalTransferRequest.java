package com.bni.orange.transaction.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalTransferRequest(
    @NotNull(message = "Source wallet ID cannot be null")
    UUID sourceWalletId,

    @NotNull(message = "Destination wallet ID cannot be null")
    UUID destinationWalletId,

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be positive")
    BigDecimal amount
) {
}
