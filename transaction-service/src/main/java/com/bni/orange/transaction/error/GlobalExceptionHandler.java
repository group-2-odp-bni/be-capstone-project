package com.bni.orange.transaction.error;

import com.bni.orange.transaction.model.response.error.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        log.error("Business exception: code={}, message='{}'", ex.getErrorCode().getCode(), ex.getMessage(), ex);
        var errorResponse = ApiErrorResponse.builder()
            .code(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .details(ex.getDetails())
            .build();
        return new ResponseEntity<>(errorResponse, ex.getErrorCode().getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        var validationErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> new ApiErrorResponse.ValidationError(fieldError.getField(), fieldError.getDefaultMessage()))
            .toList();

        log.warn("Validation error: {}", validationErrors);

        var errorCode = ErrorCode.VALIDATION_ERROR;
        var errorResponse = ApiErrorResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .validationErrors(validationErrors)
            .build();

        return new ResponseEntity<>(errorResponse, errorCode.getStatus());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleWebClientException(WebClientResponseException ex) {
        log.error("External service error: status={}, response='{}'", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);

        var errorCode = determineErrorCodeFromWebClientException(ex);

        var errorResponse = ApiErrorResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .details(Map.of(
                "external_status", ex.getStatusCode().value(),
                "external_response", ex.getResponseBodyAsString()
            ))
            .build();

        return new ResponseEntity<>(errorResponse, errorCode.getStatus());
    }

    private ErrorCode determineErrorCodeFromWebClientException(WebClientResponseException ex) {
        return ex.getStatusCode().is5xxServerError()
            ? ErrorCode.SERVICE_UNAVAILABLE
            : ErrorCode.EXTERNAL_SERVICE_ERROR;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("An unexpected error occurred", ex);
        var errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        var errorResponse = ApiErrorResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .build();
        return new ResponseEntity<>(errorResponse, errorCode.getStatus());
    }
}
