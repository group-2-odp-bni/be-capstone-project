package com.bni.orange.authentication.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, ErrorResponse>> handleBusinessException(BusinessException ex) {
        var errorCode = ex.getErrorCode();
        var errorResponse = new ErrorResponse(errorCode.getCode(), ex.getMessage());
        return new ResponseEntity<>(Map.of("error", errorResponse), errorCode.getStatus());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, ErrorResponse>> handleAccessDenied(AccessDeniedException ex) {
        var errorCode = ErrorCode.FORBIDDEN_ACCESS;
        var errorResponse = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
        return new ResponseEntity<>(Map.of("error", errorResponse), errorCode.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, ErrorResponse>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        var errorCode = ErrorCode.GENERAL_ERROR;
        var errorResponse = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
        return new ResponseEntity<>(Map.of("error", errorResponse), errorCode.getStatus());
    }

    public record ErrorResponse(
        String code,
        String message
    ) {
    }
}