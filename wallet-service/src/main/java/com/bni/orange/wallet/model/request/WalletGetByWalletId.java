package com.bni.orange.wallet.model.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record WalletGetByWalletId(@NotNull UUID wallet_id) {}
