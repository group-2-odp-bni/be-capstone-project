package com.bni.orange.api.gateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TokenRevokedException extends ResponseStatusException {
    public TokenRevokedException(String reason) {
        super(HttpStatus.UNAUTHORIZED, reason, null);
    }
}
