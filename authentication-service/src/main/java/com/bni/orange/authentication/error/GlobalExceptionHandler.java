package com.bni.orange.authentication.error;

import com.bni.orange.authentication.model.response.ApiResponse;
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

        var response = ApiResponse.<Void>builder()
            .message("Request failed")
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .build())
            .path(request.getRequestURI())
            .build();

        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
        HttpServletRequest request
    ) {
        var errorCode = ErrorCode.FORBIDDEN_ACCESS;

        var response = ApiResponse.<Void>builder()
            .message("Access denied")
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
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
        var validationErrors = new HashMap<>();
        ex.getBindingResult()
            .getFieldErrors()
            .forEach(error -> validationErrors.put(error.getField(), error.getDefaultMessage()));

        var response = ApiResponse.<Void>builder()
            .message("Validation failed")
            .error(ApiResponse.ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("Validation failed")
                .details(validationErrors)
                .build())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
        Throwable ex,
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
