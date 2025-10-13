package com.bni.orange.authentication.util;

import com.bni.orange.authentication.model.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;

public final class ResponseBuilder {

    private ResponseBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> ApiResponse<T> success(String message, T data, HttpServletRequest request) {
        return ApiResponse.<T>builder()
            .message(message)
            .data(data)
            .path(request.getRequestURI())
            .build();
    }

    public static <T> ApiResponse<T> success(String message, HttpServletRequest request) {
        return ApiResponse.<T>builder()
            .message(message)
            .path(request.getRequestURI())
            .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode, String errorMessage, HttpServletRequest request) {
        var errorDetail = ApiResponse.ErrorDetail.builder()
            .code(errorCode)
            .message(errorMessage)
            .build();

        return ApiResponse.<T>builder()
            .message(message)
            .error(errorDetail)
            .path(request.getRequestURI())
            .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode, String errorMessage, Object details, HttpServletRequest request) {
        var errorDetail = ApiResponse.ErrorDetail.builder()
            .code(errorCode)
            .message(errorMessage)
            .details(details)
            .build();

        return ApiResponse.<T>builder()
            .message(message)
            .error(errorDetail)
            .path(request.getRequestURI())
            .build();
    }
}
