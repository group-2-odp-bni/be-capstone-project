package com.bni.orange.authentication.model.response;

import com.bni.orange.authentication.base.Response;
import lombok.Builder;

@Builder
public record RegisterResponse(
    String message,
    String otpId,
    Integer expiresIn
) implements Response {
}
