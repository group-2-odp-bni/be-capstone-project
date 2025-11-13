package com.bni.orange.transaction.model.response.internal;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record BalanceUpdateResponse(
    UUID walletId,
    BigDecimal previousBalance,
    BigDecimal newBalance,
    String code,
    String message
) {}
