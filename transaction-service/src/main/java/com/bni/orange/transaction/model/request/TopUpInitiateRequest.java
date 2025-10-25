package com.bni.orange.transaction.model.request;

import com.bni.orange.transaction.model.enums.PaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TopUpInitiateRequest(
    @NotNull(message = "Payment provider is required")
    PaymentProvider provider,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000.00", message = "Amount must be at least 10,000")
    @Digits(integer = 18, fraction = 2, message = "Invalid amount format")
    BigDecimal amount,

    @NotNull(message = "Wallet ID is required")
    UUID walletId
) {
}
