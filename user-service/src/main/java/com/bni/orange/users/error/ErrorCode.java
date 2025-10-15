package com.bni.orange.users.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-1001", "User profile not found"),
    PROFILE_UPDATE_FAILED(HttpStatus.BAD_REQUEST, "USER-1002", "Failed to update user profile"),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-1010", "Email address is already in use"),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "USER-1011", "Phone number is already in use"),
    EMAIL_ALREADY_PENDING(HttpStatus.CONFLICT, "USER-1012", "This email is already pending verification by another user"),
    PHONE_ALREADY_PENDING(HttpStatus.CONFLICT, "USER-1013", "This phone number is already pending verification by another user"),

    OTP_INVALID(HttpStatus.BAD_REQUEST, "USER-2001", "Invalid or incorrect OTP code"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "USER-2002", "OTP code has expired"),
    OTP_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "USER-2003", "Too many failed OTP attempts. Account temporarily locked"),
    OTP_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-2004", "No active OTP found for verification"),
    OTP_ALREADY_VERIFIED(HttpStatus.BAD_REQUEST, "USER-2005", "This OTP has already been verified"),

    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "USER-2010", "Too many requests. Please try again later"),
    OTP_GENERATION_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "USER-2011", "OTP generation limit exceeded. Please try again later"),

    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "USER-3001", "Invalid email format"),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "USER-3002", "Invalid phone number format"),
    NO_PENDING_VERIFICATION(HttpStatus.BAD_REQUEST, "USER-3003", "No pending verification found for this field"),
    SAME_VALUE_UPDATE(HttpStatus.BAD_REQUEST, "USER-3004", "New value is the same as current value"),
    VERIFICATION_MISMATCH(HttpStatus.BAD_REQUEST, "USER-3005", "Verification token does not match pending value"),

    GENERAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "USER-9999", "An unexpected error occurred");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
