package com.bni.orange.transaction.error;

import lombok.Getter;

import java.util.Objects;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object details;

    public BusinessException(ErrorCode errorCode, String customMessage, Object details, Throwable cause) {
        super(Objects.toString(customMessage, errorCode.getMessage()), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null, null);
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        this(errorCode, customMessage, null, null);
    }

    public BusinessException(ErrorCode errorCode, Object details) {
        this(errorCode, null, details, null);
    }

    public BusinessException(ErrorCode errorCode, String customMessage, Object details) {
        this(errorCode, customMessage, details, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, null, cause);
    }

    public BusinessException(ErrorCode errorCode, Object details, Throwable cause) {
        this(errorCode, null, details, cause);
    }
}
