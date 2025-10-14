package com.bni.orange.wallet.config;

import com.bni.orange.wallet.exception.*;
import com.bni.orange.wallet.model.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);
    private static final boolean EXPOSE_ERRORS =
            "true".equalsIgnoreCase(System.getProperty("app.debug.errors", "false"));

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(NoSuchElementException ex, HttpServletRequest req) {
        log.warn("404 {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", ex.getMessage(), "Not Found"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> bodyInvalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("400 {} {} -> {}", req.getMethod(), req.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION_ERROR", details, "Validation error"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> paramInvalid(ConstraintViolationException ex, HttpServletRequest req) {
        var details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("400 {} {} -> {}", req.getMethod(), req.getRequestURI(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION_ERROR", details, "Validation error"));
    }

    @ExceptionHandler({MissingRequestHeaderException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception ex, HttpServletRequest req) {
        log.warn("400 {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION_ERROR", ex.getMessage(), "Validation error"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> badJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        var msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("400 {} {} -> bad json/body: {}", req.getMethod(), req.getRequestURI(), msg, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION_ERROR", "Malformed JSON or invalid field type: " + msg, "Validation error"));
    }

    // 409s
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException ex, HttpServletRequest req) {
        var root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("409 {} {} -> {}", req.getMethod(), req.getRequestURI(), root, ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("CONFLICT", root, "Conflict"));
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiResponse<Void>> idemKeyConflict(IdempotencyKeyConflictException ex, HttpServletRequest req) {
        log.warn("409 {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST", ex.getMessage(), "Conflict"));
    }

    @ExceptionHandler(RequestInProcessException.class)
    public ResponseEntity<ApiResponse<Void>> requestInProcess(RequestInProcessException ex, HttpServletRequest req) {
        log.warn("409 {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("REQUEST_IN_PROCESS", ex.getMessage(), "Conflict"));
    }

    @ExceptionHandler(WalletStatusConflictException.class)
    public ResponseEntity<ApiResponse<Void>> walletStatus(WalletStatusConflictException ex, HttpServletRequest req) {
        log.warn("409 {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("WALLET_STATUS_NOT_ACTIVE", ex.getMessage(), "Conflict"));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiResponse<Void>> insufficient(InsufficientFundsException ex, HttpServletRequest req) {
        log.warn("409 {} {} -> {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("NEGATIVE_BALANCE_NOT_ALLOWED", ex.getMessage(), "Conflict"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> generic(Exception ex, HttpServletRequest req) {
        log.error("500 {} {} X-Request-Id={} X-Correlation-Id={} -> {}",
                req.getMethod(), req.getRequestURI(),
                req.getHeader("X-Request-Id"), req.getHeader("X-Correlation-Id"),
                ex.getMessage(), ex);
        var details = EXPOSE_ERRORS ? (ex.getClass().getSimpleName() + ": " + ex.getMessage()) : "Unexpected error";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", details, "Server error"));
    }
}
