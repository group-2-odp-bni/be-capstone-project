package com.bni.orange.authentication.event;

import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.proto.OtpNotificationEvent;
import com.bni.orange.authentication.proto.UserRegisteredEvent;

import java.time.Instant;

public final class DomainEventFactory {

    private DomainEventFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static UserRegisteredEvent createUserRegisteredEvent(User user) {
        var builder = UserRegisteredEvent.newBuilder()
            .setUserId(user.getId().toString())
            .setPhoneNumber(user.getPhoneNumber())
            .setName(user.getName())
            .setPhoneVerified(user.getPhoneVerified() != null && user.getPhoneVerified())
            .setEmailVerified(user.getEmailVerified() != null && user.getEmailVerified())
            .setRegisteredAt(Instant.now().toEpochMilli());

        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            builder.setEmail(user.getEmail());
        }

        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().trim().isEmpty()) {
            builder.setProfileImageUrl(user.getProfileImageUrl());
        }

        return builder.build();
    }

    public static OtpNotificationEvent createOtpNotificationEvent(
        String phoneNumber,
        String otpCode,
        String userId
    ) {
        var builder = OtpNotificationEvent.newBuilder()
            .setPhoneNumber(phoneNumber)
            .setOtpCode(otpCode)
            .setEventCreatedAt(Instant.now().toEpochMilli());

        if (userId != null) {
            builder.setUserId(userId);
        }

        return builder.build();
    }

    public static OtpNotificationEvent createOtpNotificationEvent(
        String phoneNumber,
        String otpCode
    ) {
        return createOtpNotificationEvent(phoneNumber, otpCode, null);
    }
}
