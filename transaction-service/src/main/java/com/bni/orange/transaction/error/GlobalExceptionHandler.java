package com.bni.orange.transaction.error;

import com.bni.orange.transaction.model.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
        BusinessException ex,
        HttpServletRequest request
    ) {
        var errorCode = ex.getErrorCode();
        log.error("BusinessException occurred for request URI: {}. ErrorCode: {}, Message: {}",
            request.getRequestURI(), errorCode.getCode(), ex.getMessage(), ex);

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
        var validationErrors = new HashMap<String, String>();
        ex.getBindingResult()
            .getFieldErrors()
            .forEach(error -> validationErrors.put(error.getField(), error.getDefaultMessage()));

        log.warn("MethodArgumentNotValidException occurred for request URI: {}. Validation Errors: {}",
            request.getRequestURI(), validationErrors);

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

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClientException(
        WebClientResponseException ex,
        HttpServletRequest request
    ) {
        log.error("External service error: status={}, response='{}'",
            ex.getStatusCode(), ex.getResponseBodyAsString(), ex);

        var errorCode = determineErrorCodeFromWebClientException(ex);

        var response = ApiResponse.<Void>builder()
            .message("External service error")
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .details(Map.of(
                    "external_status", ex.getStatusCode().value(),
                    "external_response", ex.getResponseBodyAsString()
                ))
                .build())
            .path(request.getRequestURI())
            .build();

        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    private ErrorCode determineErrorCodeFromWebClientException(WebClientResponseException ex) {
        return ex.getStatusCode().is5xxServerError()
            ? ErrorCode.SERVICE_UNAVAILABLE
            : ErrorCode.EXTERNAL_SERVICE_ERROR;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("An unexpected error occurred", ex);
        var errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

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
