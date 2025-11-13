package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.PaymentProvider;

import java.math.BigDecimal;

public record PaymentMethodResponse(
    PaymentProvider provider,
    String providerName,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    BigDecimal feeAmount,
    BigDecimal feePercentage,
    String iconUrl,
    Integer displayOrder
) {
}
