package com.bni.orange.wallet.exception.business;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Exception for external service communication errors.
 * Following the pattern from notification-worker for client exceptions.
 */
public class ExternalServiceException extends ApiException {

    public ExternalServiceException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }

    public static ExternalServiceException userServiceNotAvailable(String details) {
        return new ExternalServiceException("User service is not available: " + details);
    }

    public static ExternalServiceException userNotFound(String identifier) {
        return new ExternalServiceException("User not found in user service: " + identifier);
    }

    /**
     * Exception for 4xx client errors from external services
     */
    public static class ClientErrorException extends ExternalServiceException {
        public ClientErrorException(String message) {
            super("Client error: " + message);
        }
    }

    /**
     * Exception for 5xx server errors from external services
     */
    public static class ServerErrorException extends ExternalServiceException {
        public ServerErrorException(String message) {
            super("Server error: " + message);
        }
    }

    /**
     * Exception for timeout errors
     */
    public static class TimeoutException extends ExternalServiceException {
        public TimeoutException(String message) {
            super("Timeout error: " + message);
        }
    }
}
