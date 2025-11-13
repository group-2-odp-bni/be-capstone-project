package com.bni.orange.wallet.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record WalletCreateRequest(
        @NotNull UUID user_id,
        String currency
) {}