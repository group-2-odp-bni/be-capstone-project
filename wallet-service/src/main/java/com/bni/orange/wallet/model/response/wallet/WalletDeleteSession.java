package com.bni.orange.wallet.model.response.wallet;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class WalletDeleteSession {
    private UUID walletId;
    private UUID ownerId;
    private String nonce;
    private OffsetDateTime createdAt;
}
