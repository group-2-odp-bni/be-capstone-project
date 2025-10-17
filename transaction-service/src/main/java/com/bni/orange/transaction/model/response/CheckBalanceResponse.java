package com.bni.orange.transaction.model.response;

import java.math.BigDecimal;

public record CheckBalanceResponse(
        BigDecimal balance
) {
}
