package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.WalletType;

import java.util.UUID;


public record WalletResolutionResponse(
    UUID walletId,
    String walletName,
    WalletType walletType,
    UUID ownerId,
    String currency
) {
}
