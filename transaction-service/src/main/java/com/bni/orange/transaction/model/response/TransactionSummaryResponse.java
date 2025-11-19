package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionSummaryResponse(
    UUID id,
    String transactionRef,
    TransactionType type,
    TransactionStatus status,
    BigDecimal amount,
    String currency,
    String displayName,
    String displaySubtitle,
    OffsetDateTime createdAt
) {
}
