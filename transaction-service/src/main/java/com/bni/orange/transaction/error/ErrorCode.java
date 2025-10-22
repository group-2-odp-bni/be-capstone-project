package com.bni.orange.transaction.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Transaction Errors (1xxx)
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "TXN-1001", "Transaction not found"),
    TRANSACTION_ALREADY_PROCESSED(HttpStatus.CONFLICT, "TXN-1002", "Transaction has already been processed"),
    TRANSACTION_EXPIRED(HttpStatus.GONE, "TXN-1003", "Transaction has expired"),
    INVALID_TRANSACTION_STATE(HttpStatus.BAD_REQUEST, "TXN-1004", "Transaction is in invalid state for this operation"),
    DUPLICATE_TRANSACTION(HttpStatus.CONFLICT, "TXN-1005", "Duplicate transaction detected (idempotency key already used)"),

    // User & Recipient Errors (2xxx)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "TXN-2001", "User not found"),
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "TXN-2002", "Recipient not found with the provided phone number"),
    SELF_TRANSFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "TXN-2003", "Cannot transfer to yourself"),
    RECIPIENT_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "TXN-2004", "Recipient does not have an active wallet"),
    RECIPIENT_WALLET_INACTIVE(HttpStatus.BAD_REQUEST, "TXN-2005", "Recipient's wallet is not active"),
    RECIPIENT_DEFAULT_WALLET_NOT_SET(HttpStatus.NOT_FOUND, "TXN-2006", "Recipient has not set a default wallet for receiving transfers"),

    // Wallet & Balance Errors (3xxx)
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "TXN-3001", "Wallet not found"),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "TXN-3002", "Insufficient balance for this transaction"),
    WALLET_INACTIVE(HttpStatus.BAD_REQUEST, "TXN-3003", "Wallet is not active"),
    BALANCE_ADJUSTMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TXN-3004", "Failed to adjust wallet balance"),

    // PIN & Security Errors (4xxx)
    INVALID_PIN(HttpStatus.UNAUTHORIZED, "TXN-4001", "Invalid PIN provided"),
    PIN_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "TXN-4002", "PIN verification failed"),

    // Validation Errors (5xxx)
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "TXN-5000", "Input validation failed"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "TXN-5001", "Invalid transaction amount"),
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "TXN-5002", "Invalid phone number format"),
    INVALID_CURRENCY(HttpStatus.BAD_REQUEST, "TXN-5003", "Invalid or unsupported currency"),
    MISSING_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "TXN-5004", "Required field is missing"),

    // External Service Errors (6xxx)
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "TXN-6000", "Error communicating with an external service"),
    USER_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "TXN-6001", "User service is temporarily unavailable"),
    WALLET_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "TXN-6002", "Wallet service is temporarily unavailable"),
    AUTH_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "TXN-6003", "Authentication service is temporarily unavailable"),
    EXTERNAL_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "TXN-6004", "External service request timed out"),
    WALLET_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TXN-6005", "Failed to update wallet balance"),

    // Top-Up & Payment Errors (7xxx)
    PAYMENT_PROVIDER_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "TXN-7001", "Payment provider is not available"),
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "TXN-7002", "Payment provider returned an error"),
    VIRTUAL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "TXN-7003", "Virtual account not found"),
    VIRTUAL_ACCOUNT_EXPIRED(HttpStatus.GONE, "TXN-7004", "Virtual account has expired"),
    VIRTUAL_ACCOUNT_ALREADY_PAID(HttpStatus.CONFLICT, "TXN-7005", "Virtual account has already been paid"),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "TXN-7006", "Invalid status for this operation"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "TXN-7007", "Invalid request"),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "TXN-7008", "Invalid webhook signature"),
    MISSING_SIGNATURE(HttpStatus.BAD_REQUEST, "TXN-7009", "Missing signature header"),
    SIGNATURE_VALIDATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "TXN-7010", "Signature validation error"),
    INVALID_TIMESTAMP(HttpStatus.BAD_REQUEST, "TXN-7011", "Invalid or expired timestamp"),

    // Authorization Errors (8xxx)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "TXN-8001", "Unauthorized access"),
    WALLET_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TXN-8002", "User does not have access to this wallet"),
    WALLET_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "TXN-8003", "Wallet is not active"),
    INSUFFICIENT_PERMISSIONS(HttpStatus.FORBIDDEN, "TXN-8004", "User role does not have required permissions"),
    MEMBERSHIP_NOT_ACTIVE(HttpStatus.FORBIDDEN, "TXN-8005", "Wallet membership is not active"),
    TRANSACTION_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "TXN-8006", "Transaction exceeds wallet limit"),
    DAILY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "TXN-8007", "Daily transaction limit exceeded"),
    SPENDING_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "TXN-8008", "Personal spending limit exceeded"),

    // General Errors (9xxx)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "TXN-9001", "An unexpected error occurred"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "TXN-9002", "Service is temporarily unavailable");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
