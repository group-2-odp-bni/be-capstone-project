package com.bni.orange.wallet.model.response;

public record ApiResponse<T>(
        Object error,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(String m, T d){ return new ApiResponse<>(null, m, d); }
    public static <T> ApiResponse<T> created(String m, T d){ return new ApiResponse<>(null, m, d); }
    public static <T> ApiResponse<T> fail(String code, String details, String m){
        return new ApiResponse<>(new ApiError(code, details), m, null);
    }
}
