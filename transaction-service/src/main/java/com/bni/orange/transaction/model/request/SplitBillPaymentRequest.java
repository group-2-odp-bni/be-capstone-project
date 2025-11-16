package com.bni.orange.transaction.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SplitBillPaymentRequest(
    @NotBlank(message = "Bill ID is required")
    @Size(max = 100, message = "Bill ID cannot exceed 100 characters")
    String billId,

    @NotBlank(message = "Member ID is required")
    @Size(max = 100, message = "Member ID cannot exceed 100 characters")
    String memberId,

    @NotBlank(message = "Bill title is required")
    @Size(max = 255, message = "Bill title cannot exceed 255 characters")
    String billTitle,

    @NotNull(message = "Bill owner user ID is required")
    UUID billOwnerUserId,

    @NotNull(message = "Source wallet ID is required")
    UUID sourceWalletId,

    @NotNull(message = "Destination wallet ID is required")
    UUID destinationWalletId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    @Digits(integer = 18, fraction = 2, message = "Invalid amount format")
    BigDecimal amount
) {}
