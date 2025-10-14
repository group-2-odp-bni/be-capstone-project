package com.bni.orange.transaction.model.request;

import java.math.BigDecimal;
import java.util.UUID;

public record TopupRequest(
        UUID walletId,
        String transactionId,
        UUID userId,
        BigDecimal amount
) {
}
