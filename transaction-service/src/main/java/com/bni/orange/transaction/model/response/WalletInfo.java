package com.bni.orange.transaction.model.response;

import com.bni.orange.transaction.model.enums.WalletRole;
import com.bni.orange.transaction.model.enums.WalletType;
import lombok.Builder;

import java.util.UUID;

@Builder
public record WalletInfo(
    UUID id,
    String name,
    WalletType type,
    WalletRole userRole
) {
}
