package com.bni.orange.transaction.model.response;

import lombok.Builder;

@Builder
public record PinVerifyResponse(
    String pin,
    Boolean valid,
    String message
) {
    public static PinVerifyResponse request(String pin) {
        return PinVerifyResponse.builder()
            .pin(pin)
            .build();
    }
}
