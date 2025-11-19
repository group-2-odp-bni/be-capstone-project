package com.bni.orange.users.event;

import com.bni.orange.users.model.entity.UserProfile;
import com.bni.orange.users.proto.OtpEmailNotificationEvent;
import com.bni.orange.users.proto.OtpPhoneNotificationEvent;
import com.bni.orange.users.proto.UserProfileEmailVerifiedEvent;
import com.bni.orange.users.proto.UserProfileNameUpdatedEvent;
import com.bni.orange.users.proto.UserProfilePhoneVerifiedEvent;

import java.time.Instant;
import java.util.UUID;

public final class ProfileEventFactory {

    private ProfileEventFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static UserProfileEmailVerifiedEvent createEmailVerifiedEvent(UserProfile profile) {
        return UserProfileEmailVerifiedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setUserId(profile.getId().toString())
            .setEmail(profile.getEmail())
            .setVerifiedAt(Instant.now().toEpochMilli())
            .build();
    }

    public static UserProfilePhoneVerifiedEvent createPhoneVerifiedEvent(UserProfile profile) {
        return UserProfilePhoneVerifiedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setUserId(profile.getId().toString())
            .setPhoneNumber(profile.getPhoneNumber())
            .setVerifiedAt(Instant.now().toEpochMilli())
            .build();
    }

    public static UserProfileNameUpdatedEvent createNameUpdatedEvent(UserProfile profile) {
        return UserProfileNameUpdatedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setUserId(profile.getId().toString())
            .setName(profile.getName())
            .setUpdatedAt(Instant.now().toEpochMilli())
            .build();
    }

    public static OtpEmailNotificationEvent createOtpEmailEvent(
        String email,
        String otpCode,
        UUID userId
    ) {
        return OtpEmailNotificationEvent.newBuilder()
            .setEmail(email)
            .setOtpCode(otpCode)
            .setUserId(userId.toString())
            .setEventCreatedAt(Instant.now().toEpochMilli())
            .build();
    }

    public static OtpPhoneNotificationEvent createOtpPhoneEvent(
        String phoneNumber,
        String otpCode,
        UUID userId
    ) {
        return OtpPhoneNotificationEvent.newBuilder()
            .setPhoneNumber(phoneNumber)
            .setOtpCode(otpCode)
            .setUserId(userId.toString())
            .setEventCreatedAt(Instant.now().toEpochMilli())
            .build();
    }
}
