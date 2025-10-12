package com.bni.orange.authentication.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinChangeRequest(
    @NotBlank(message = "Current PIN cannot be blank")
    @Pattern(regexp = "\\d{6}", message = "Current PIN must be exactly 6 digits")
    String currentPin,

    @NotBlank(message = "New PIN cannot be blank")
    @Pattern(regexp = "\\d{6}", message = "New PIN must be exactly 6 digits")
    String newPin
) {
}