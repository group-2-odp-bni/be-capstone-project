package com.bni.orange.wallet.model.response.internal;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceUpdateResponse(
    UUID walletId,
    BigDecimal previousBalance,
    BigDecimal newBalance,
    String code,
    String message
) {}
