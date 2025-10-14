package com.bni.orange.wallet.model.request;

import jakarta.validation.constraints.NotBlank;

public record WalletGetByPhoneNumber(
        @NotBlank String phone,
        String currency
) {}
