package com.bni.orange.authentication.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // User & General (1xxx)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-1001", "User not found for the given identifier."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH-1002", "A user with this phone number already exists."),

    // OTP Flow (2xxx)
    OTP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH-2001", "Please wait before requesting another OTP."),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "AUTH-2002", "The provided OTP is invalid or has expired."),
    INVALID_CAPTCHA(HttpStatus.BAD_REQUEST, "AUTH-2003", "Invalid or missing CAPTCHA. Please try again."),

    // PIN & Login Flow (3xxx)
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH-3001", "Account is temporarily locked due to too many failed attempts."),
    INVALID_PIN(HttpStatus.UNAUTHORIZED, "AUTH-3002", "The provided PIN is incorrect."),
    INVALID_CURRENT_PIN(HttpStatus.BAD_REQUEST, "AUTH-3003", "The current PIN provided is incorrect."),
    PIN_NOT_SET(HttpStatus.BAD_REQUEST, "AUTH-3004", "PIN has not been set for this account."),

    // Token & Session Flow (4xxx)
    INVALID_TOKEN_SCOPE(HttpStatus.FORBIDDEN, "AUTH-4001", "This token does not have the required scope for this action."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-4002", "Refresh token has expired. Please log in again."),
    TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH-4003", "Token reuse detected. All sessions have been terminated for security."),
    SESSION_TERMINATION_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH-4004", "You are not authorized to terminate this session."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH-4005", "Session not found."),

    // General Access & Server Errors (9xxx)
    FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "AUTH-9001", "You do not have permission to access this resource."),
    GENERAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH-9999", "An unexpected error occurred.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}