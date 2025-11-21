package com.bni.orange.transaction.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record TransferInitiateRequest(
    @NotNull(message = "Receiver user ID is required")
    UUID receiverUserId,

    @NotNull(message = "Receiver wallet ID is required")
    UUID receiverWalletId,

    @NotNull(message = "Sender wallet ID is required")
    UUID senderWalletId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    @Digits(integer = 18, fraction = 2, message = "Invalid amount format")
    BigDecimal amount,

    @Size(max = 255, message = "Notes cannot exceed 255 characters")
    String notes,

    // NEW - optional fields untuk Split Bill
    String splitBillId,

    String splitBillMemberId,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    String currency
) {
    public TransferInitiateRequest {
        if (currency == null || currency.isBlank()) {
            currency = "IDR";
        }
    }
}
