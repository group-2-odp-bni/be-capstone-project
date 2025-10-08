public record GlobalResponse<T>(
        String code,
        String messege,
        T data
        ) {
}