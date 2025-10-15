package com.bni.orange.authentication.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinRequest(
    @NotBlank(message = "PIN cannot be blank")
    @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
    String pin
) {
}