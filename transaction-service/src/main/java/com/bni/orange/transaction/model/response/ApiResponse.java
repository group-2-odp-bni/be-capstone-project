package com.bni.orange.transaction.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    String status,
    String message,
    T data,
    OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .status("success")
            .data(data)
            .timestamp(OffsetDateTime.now())
            .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
            .status("success")
            .message(message)
            .data(data)
            .timestamp(OffsetDateTime.now())
            .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .status("error")
            .message(message)
            .timestamp(OffsetDateTime.now())
            .build();
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
            .status("error")
            .message(message)
            .data(data)
            .timestamp(OffsetDateTime.now())
            .build();
    }
}
