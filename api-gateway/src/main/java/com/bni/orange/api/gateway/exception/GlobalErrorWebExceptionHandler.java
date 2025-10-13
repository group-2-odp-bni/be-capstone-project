package com.bni.orange.api.gateway.exception;

import com.bni.orange.api.gateway.model.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Builder
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

        var apiResponse = ApiResponse
            .<Void>builder()
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
            case TokenRevokedException tre -> ErrorResponseInfo.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message("Token has been revoked")
                .errorCode("TOKEN_REVOKED")
                .errorMessage(tre.getReason())
                .build();

            case JwtAuthenticationException jae -> ErrorResponseInfo.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message("Authentication failed")
                .errorCode(jae.getErrorCode())
                .errorMessage(jae.getReason())
                .build();

            case ServerWebInputException ignored -> ErrorResponseInfo.builder()
                .status(HttpStatus.BAD_REQUEST)
                .message("Request validation failed")
                .errorCode("INVALID_REQUEST")
                .errorMessage("Invalid request format")
                .build();

            case ResponseStatusException rse -> {
                var httpStatus = HttpStatus.valueOf(rse.getStatusCode().value());
                var reason = rse.getReason() != null ? rse.getReason() : "An error occurred";
                yield ErrorResponseInfo.builder()
                    .status(httpStatus)
                    .message("Gateway request failed")
                    .errorCode(httpStatus.name())
                    .errorMessage(reason)
                    .build();
            }

            case AccessDeniedException ignored -> ErrorResponseInfo.builder()
                .status(HttpStatus.FORBIDDEN)
                .message("Access denied")
                .errorCode("ACCESS_DENIED")
                .errorMessage("You do not have permission to access this resource")
                .build();

            case AuthenticationException ignored -> ErrorResponseInfo.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .message("Authentication required")
                .errorCode("AUTHENTICATION_FAILED")
                .errorMessage("Authentication failed")
                .build();

            default -> {
                log.error("Unhandled exception in API Gateway", ex);
                yield ErrorResponseInfo.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Internal server error")
                    .errorCode("INTERNAL_ERROR")
                    .errorMessage("An unexpected error occurred")
                    .build();
            }
        };
    }

    private Mono<Void> writeResponse(ServerHttpResponse response, ApiResponse<?> apiResponse) {
        try {
            var jsonBytes = objectMapper.writeValueAsBytes(apiResponse);
            var buffer = response.bufferFactory().wrap(jsonBytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response for path: {}", apiResponse.getPath(), e);
            var fallback = "{\"message\":\"Internal Server Error\",\"error\":{\"code\":\"SERIALIZATION_ERROR\",\"message\":\"Could not process error response\"}}";
            var buffer = response.bufferFactory().wrap(fallback.getBytes());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.writeWith(Mono.just(buffer));
        }
    }
}