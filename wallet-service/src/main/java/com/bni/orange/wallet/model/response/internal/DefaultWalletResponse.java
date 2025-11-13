package com.bni.orange.wallet.model.response.internal;

import com.bni.orange.wallet.model.enums.WalletType;
import lombok.Builder;

import java.util.UUID;

@Builder
public record DefaultWalletResponse(
    UUID userId,
    boolean hasDefaultWallet,
    UUID walletId,
    String walletName,
    WalletType walletType,
    String currency
) {}
