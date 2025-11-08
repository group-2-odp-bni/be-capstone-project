package com.bni.orange.wallet.model.request.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ValidateWalletOwnershipRequest(
    @NotNull
    UUID userId,

    @NotNull
    @Size(min = 1, message = "At least one wallet ID must be provided")
    List<UUID> walletIds
) {
}
