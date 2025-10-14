package com.bni.orange.users.error;

import com.bni.orange.users.model.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request
    ) {
        var errorCode = ex.getErrorCode();

        log.warn("Business exception: {} - {}", errorCode.getCode(), ex.getMessage());

        var response = ApiResponse.<Void>builder()
            .message("Request failed")
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .build())
            .path(request.getRequestURI())
            .build();

        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        log.warn("Validation error: {}", ex.getMessage());

        var validationErrors = new HashMap<String, String>();
        ex.getBindingResult()
            .getFieldErrors()
            .forEach(error -> validationErrors.put(error.getField(), error.getDefaultMessage()));

        var response = ApiResponse.<Void>builder()
            .message("Validation failed")
            .error(ApiResponse.ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(validationErrors)
                .build())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        log.error("Access denied: {}", ex.getMessage());

        var response = ApiResponse.<Void>builder()
            .message("Access denied")
            .error(ApiResponse.ErrorDetail.builder()
                .code("ACCESS_DENIED")
                .message("You do not have permission to access this resource")
                .build())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(403).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("Unexpected error occurred", ex);

        var errorCode = ErrorCode.GENERAL_ERROR;

        var response = ApiResponse.<Void>builder()
            .message("An unexpected error occurred")
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build())
            .path(request.getRequestURI())
            .build();

        return new ResponseEntity<>(response, errorCode.getStatus());
    }
}
