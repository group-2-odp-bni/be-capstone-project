package com.bni.orange.transaction.model.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record WalletResponse(
    UUID id,
    UUID userId,
    String currency,
    String status,
    BigDecimal balanceSnapshot,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
