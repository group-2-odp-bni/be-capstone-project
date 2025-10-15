package com.bni.orange.wallet.model.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BalanceResponse(
        UUID wallet_id,
        String currency,
        BigDecimal balance,
        OffsetDateTime as_of
) {}
