package com.bni.orange.authentication.model.response;

import lombok.Builder;

@Builder
public record StateTokenResponse(
    String stateToken,
    long expiresIn
) {
}