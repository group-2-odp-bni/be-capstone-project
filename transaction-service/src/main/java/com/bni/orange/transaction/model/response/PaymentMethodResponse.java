package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.PaymentProvider;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
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
