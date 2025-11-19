package com.bni.orange.authentication.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
    @NotBlank
    @Pattern(regexp = "^(\\+628|08|8)[0-9]{9,11}$", message = "Invalid phone number format")
    String phoneNumber,

    @NotBlank
    @Size(min = 6, max = 6)
    String otp
) {
}