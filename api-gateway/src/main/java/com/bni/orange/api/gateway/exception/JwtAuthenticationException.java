package com.bni.orange.api.gateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
public class JwtAuthenticationException extends ResponseStatusException {

    private final String errorCode;

    public JwtAuthenticationException(String errorCode, String reason) {
        super(HttpStatus.UNAUTHORIZED, reason);
        this.errorCode = errorCode;
    }

    public static JwtAuthenticationException tokenExpired() {
        return new JwtAuthenticationException("TOKEN_EXPIRED", "Access token has expired");
    }

    public static JwtAuthenticationException tokenInvalid() {
        return new JwtAuthenticationException("TOKEN_INVALID", "Invalid token signature or format");
    }

    public static JwtAuthenticationException tokenMalformed() {
        return new JwtAuthenticationException("TOKEN_MALFORMED", "Malformed JWT token");
    }

    public static JwtAuthenticationException tokenMissing() {
        return new JwtAuthenticationException("TOKEN_MISSING", "Authorization token is required");
    }
}
