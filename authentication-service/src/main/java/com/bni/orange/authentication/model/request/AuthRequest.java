package com.bni.orange.authentication.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthRequest(
    @NotBlank
    @Pattern(regexp = "^(\\+628|08)[0-9]{8,11}$", message = "Invalid phone number format")
    String phoneNumber,

    @NotBlank(message = "Captcha token is required")
    String captchaToken
) {
}