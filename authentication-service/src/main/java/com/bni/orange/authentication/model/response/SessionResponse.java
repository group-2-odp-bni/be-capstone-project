package com.bni.orange.authentication.model.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record SessionResponse(
    UUID sessionId,
    String ipAddress,
    String userAgent,
    Instant lastUsedAt,
    boolean isCurrent
) {
}