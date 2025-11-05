package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record VirtualAccountResponse(
    UUID id,
    String vaNumber,
    String accountName,
    UUID transactionId,
    String transactionRef,
    PaymentProvider provider,
    VirtualAccountStatus status,
    BigDecimal amount,
    BigDecimal paidAmount,
    OffsetDateTime expiresAt,
    OffsetDateTime paidAt,
    OffsetDateTime createdAt
) {
}
