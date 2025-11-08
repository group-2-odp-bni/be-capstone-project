package com.bni.orange.transaction.model.request.internal;

import java.util.List;
import java.util.UUID;

public record ValidateWalletOwnershipRequest(
    UUID userId,
    List<UUID> walletIds
) {
    public static ValidateWalletOwnershipRequest of(UUID userId, List<UUID> walletIds) {
        return new ValidateWalletOwnershipRequest(userId, walletIds);
    }
}