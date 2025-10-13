package com.bni.orange.api.gateway.exception;

import com.bni.orange.api.gateway.model.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private record ErrorResponseInfo(
        HttpStatus status,
        String message,
        String errorCode,
        String errorMessage
    ) {
    }

    @NonNull
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, @NonNull Throwable ex) {
        var response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        var errorInfo = determineErrorDetails(ex);
        response.setStatusCode(errorInfo.status());

        var apiResponse = ApiResponse.<Void>builder()
            .message(errorInfo.message())
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorInfo.errorCode())
                .message(errorInfo.errorMessage())
                .build())
            .path(exchange.getRequest().getURI().getPath())
            .build();

        return writeResponse(response, apiResponse);
    }

    private ErrorResponseInfo determineErrorDetails(Throwable ex) {
        return switch (ex) {
            case TokenRevokedException tre ->
                new ErrorResponseInfo(HttpStatus.UNAUTHORIZED, "Token has been revoked", "TOKEN_REVOKED", tre.getReason());
            case org.springframework.web.server.ServerWebInputException ignored ->
                new ErrorResponseInfo(HttpStatus.BAD_REQUEST, "Request validation failed", "INVALID_REQUEST", "Invalid request format");
            case ResponseStatusException rse -> {
                HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
                String code = status != null ? status.name() : "GATEWAY_ERROR";
                String msg = rse.getReason() != null ? rse.getReason() : ex.getMessage();
                yield new ErrorResponseInfo(status, "Gateway request failed", code, msg);
            }
            case org.springframework.security.access.AccessDeniedException ade ->
                new ErrorResponseInfo(HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED", "You do not have permission to access this resource");
            case org.springframework.security.core.AuthenticationException ignored ->
                new ErrorResponseInfo(HttpStatus.UNAUTHORIZED, "Authentication required", "AUTHENTICATION_FAILED", "Authentication failed");
            default -> {
                log.error("Unhandled exception in API Gateway", ex);
                yield new ErrorResponseInfo(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_ERROR", "An unexpected error occurred");
            }
        };
    }

    private Mono<Void> writeResponse(ServerHttpResponse response, ApiResponse<?> apiResponse) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(apiResponse);
            DataBuffer buffer = response.bufferFactory().wrap(jsonBytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response for path: {}", apiResponse.getPath(), e);
            String fallback = "{\"message\":\"Internal Server Error\",\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"Could not process error response\"}}";
            DataBuffer buffer = response.bufferFactory().wrap(fallback.getBytes());
            if (response.getStatusCode() == null || !response.getStatusCode().isError()) {
                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return response.writeWith(Mono.just(buffer));
        }
    }
}
