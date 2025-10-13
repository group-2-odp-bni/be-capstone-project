package com.bni.orange.transaction.model.response;

public record ApiResponse<T>(
        String error,
        String message,
        T data
        ) {
}