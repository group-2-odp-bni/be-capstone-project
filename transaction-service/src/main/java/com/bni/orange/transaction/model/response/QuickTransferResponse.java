package com.bni.orange.transaction.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuickTransferResponse(
    UUID id,
    UUID userId,
    UUID recipientUserId,
    String recipientName,
    String recipientPhone,
    String recipientAvatarInitial,
    OffsetDateTime lastUsedAt,
    Integer usageCount,
    Integer displayOrder,
    OffsetDateTime createdAt
) {
}
