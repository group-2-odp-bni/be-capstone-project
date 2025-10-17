package com.bni.orange.transaction.model.request;

import java.util.UUID;

public record CheckBalanceRequest(
        UUID walletId
) {
}
