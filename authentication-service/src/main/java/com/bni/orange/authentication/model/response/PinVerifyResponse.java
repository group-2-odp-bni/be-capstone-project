package com.bni.orange.authentication.model.response;

import lombok.Builder;

/**
 * Response DTO for PIN verification
 */
@Builder
public record PinVerifyResponse(
    boolean valid,
    String message
) {
    public static PinVerifyResponse success() {
        return PinVerifyResponse.builder()
            .valid(true)
            .message("PIN is valid")
            .build();
    }

    public static PinVerifyResponse failed() {
        return PinVerifyResponse.builder()
            .valid(false)
            .message("PIN is invalid")
            .build();
    }
}
