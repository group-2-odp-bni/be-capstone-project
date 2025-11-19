package com.bni.orange.transaction.model.response.internal;

import java.util.Map;
import java.util.UUID;

public record ValidateWalletOwnershipResponse(
    boolean isOwner,
    Map<UUID, String> walletNames
) {
}