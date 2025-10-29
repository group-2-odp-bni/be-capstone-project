package com.bni.orange.transaction.model.request;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BalanceAdjustmentRequest(
    BigDecimal amount,
    String reason,
    String description
) {
}
