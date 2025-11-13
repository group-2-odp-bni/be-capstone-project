package com.bni.orange.wallet.model.request;

import jakarta.validation.constraints.NotBlank;

public record BalanceGetByPhoneNumber(
        @NotBlank String phone,
        String currency
) {}
