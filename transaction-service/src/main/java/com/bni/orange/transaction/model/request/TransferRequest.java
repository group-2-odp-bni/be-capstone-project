package com.bni.orange.transaction.model.request;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        UUID walletId,
        UUID senderId,
        UUID receiverId,
        BigDecimal amount

) {
}

