package com.bni.orange.wallet.model.response;

import com.bni.orange.wallet.model.enums.WalletStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID user_id,
        String currency,
        WalletStatus status,
        BigDecimal balance_snapshot,
        OffsetDateTime created_at,
        OffsetDateTime updated_at
) { }
