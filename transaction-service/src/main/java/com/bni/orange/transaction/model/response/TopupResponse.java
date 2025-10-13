package com.bni.orange.transaction.model.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TopupResponse(
        UUID walletId,
        String transactionId,
        UUID userId,
        BigDecimal amount
) {
}
