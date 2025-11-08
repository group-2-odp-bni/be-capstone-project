package com.bni.orange.wallet.model.response.internal;

import java.util.Map;
import java.util.UUID;

/**
 * Response for a wallet ownership validation request.
 *
 * @param isOwner True if the user owns all the requested wallets, false otherwise.
 * @param walletNames Map of wallet ID to wallet name for all requested wallets (only populated if isOwner is true).
 */
public record ValidateWalletOwnershipResponse(
    boolean isOwner,
    Map<UUID, String> walletNames
) {
    public static ValidateWalletOwnershipResponse notOwner() {
        return new ValidateWalletOwnershipResponse(false, Map.of());
    }

    public static ValidateWalletOwnershipResponse owner(Map<UUID, String> walletNames) {
        return new ValidateWalletOwnershipResponse(true, walletNames);
    }
}
