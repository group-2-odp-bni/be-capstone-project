package com.bni.orange.users.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final String message;
    private final T data;
    private final ErrorDetail error;
    @Builder.Default
    private final Instant timestamp = Instant.now();
    private final String path;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .message("Success")
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .message(message)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .message(message)
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .message("Request failed")
            .error(ErrorDetail.builder()
                .code(code)
                .message(message)
                .build())
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
            .message("Request failed")
            .error(ErrorDetail.builder()
                .code(code)
                .message(message)
                .details(details)
                .build())
            .build();
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private final String code;
        private final String message;
        private final Object details;
    }
}
