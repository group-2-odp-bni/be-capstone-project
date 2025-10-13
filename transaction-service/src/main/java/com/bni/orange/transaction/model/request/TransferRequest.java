package com.bni.orange.transaction.model.request;

import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferRequest(
        UUID walletId,
        String transactionId,
        UUID senderId,
        UUID receiverId,
        BigDecimal amount

) {
}

