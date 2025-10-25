package com.bni.orange.authentication.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for PIN verification (without login)
 * Used by transaction-service to verify PIN before processing transfer
 */
public record PinVerifyRequest(
    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "^\\d{6}$", message = "PIN must be exactly 6 digits")
    String pin
) {
}
