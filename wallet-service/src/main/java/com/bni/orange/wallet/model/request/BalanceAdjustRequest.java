package com.bni.orange.wallet.model.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BalanceAdjustRequest(
        @NotNull BigDecimal amount,
        @NotNull String reason
) {}
