package com.bni.orange.authentication.model.response;

import lombok.Builder;

@Builder
public record OtpResponse(
    String channel,
    Integer expiresIn
) {
}
