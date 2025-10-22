package com.bni.orange.transaction.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record TopUpCallbackRequest(
    @NotBlank(message = "VA number is required")
    String vaNumber,

    @NotNull(message = "Paid amount is required")
    BigDecimal paidAmount,

    @NotBlank(message = "Payment reference is required")
    String paymentReference,

    @NotBlank(message = "Payment timestamp is required")
    String paymentTimestamp,

    String signature,

    Map<String, Object> additionalData
) {
}
