package com.bni.orange.transaction.model.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BalanceResponse(
    BigDecimal balance,
    String currency,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter
) {
}
