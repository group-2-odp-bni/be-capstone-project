package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
    UUID id,
    String transactionRef,
    TransactionType type,
    TransactionStatus status,
    BigDecimal amount,
    BigDecimal fee,
    BigDecimal totalAmount,
    String currency,
    UUID senderUserId,
    UUID senderWalletId,
    UUID receiverUserId,
    UUID receiverWalletId,
    String receiverName,
    String receiverPhone,
    String description,
    String notes,
    Map<String, Object> metadata,
    OffsetDateTime completedAt,
    OffsetDateTime failedAt,
    String failureReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
