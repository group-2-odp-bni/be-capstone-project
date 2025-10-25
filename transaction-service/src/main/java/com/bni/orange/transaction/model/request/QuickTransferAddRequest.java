package com.bni.orange.transaction.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record QuickTransferAddRequest(
    @NotNull(message = "Recipient user ID is required")
    UUID recipientUserId,

    @NotBlank(message = "Recipient name is required")
    String recipientName,

    @NotBlank(message = "Recipient phone is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    String recipientPhone
) {
}
