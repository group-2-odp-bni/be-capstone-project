package com.bni.orange.transaction.model.response.internal;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record UserWalletsResponse(
    List<UUID> walletIds
) {
}
