package com.bni.orange.wallet.model.response.internal;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record UserWalletsResponse(
    List<UUID> walletIds
) {
}
