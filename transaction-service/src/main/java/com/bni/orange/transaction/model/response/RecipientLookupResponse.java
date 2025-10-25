package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.WalletType;
import lombok.Builder;

import java.util.UUID;

@Builder
public record RecipientLookupResponse(
    UUID userId,
    String name,
    String phoneNumber,
    String formattedPhone,
    String profileImageUrl,
    boolean hasWallet,
    UUID walletId,
    String walletCurrency,
    String walletStatus,
    String walletName,
    WalletType walletType
) {
}
