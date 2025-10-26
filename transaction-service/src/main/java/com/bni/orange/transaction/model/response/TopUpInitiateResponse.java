package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record TopUpInitiateResponse(
    UUID transactionId,
    String transactionRef,
    UUID virtualAccountId,
    String vaNumber,
    PaymentProvider provider,
    VirtualAccountStatus status,
    BigDecimal amount,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    WalletInfo wallet
) {
}
