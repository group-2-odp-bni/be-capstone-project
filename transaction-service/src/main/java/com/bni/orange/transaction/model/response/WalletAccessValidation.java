package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.WalletRole;
import com.bni.orange.transaction.model.enums.WalletStatus;
import com.bni.orange.transaction.model.enums.WalletType;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record WalletAccessValidation(
    boolean hasAccess,
    WalletRole userRole,
    BigDecimal spendingLimit,
    WalletStatus walletStatus,
    WalletType walletType,
    String walletName,
    String denialReason
) {
}
