package com.bni.orange.transaction.model.response;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record UserProfileResponse(
    UUID id,
    String name,
    String email,
    String phoneNumber,
    String profileImageUrl,
    Boolean emailVerified,
    Boolean phoneVerified,
    OffsetDateTime emailVerifiedAt,
    OffsetDateTime phoneVerifiedAt
) {
}
