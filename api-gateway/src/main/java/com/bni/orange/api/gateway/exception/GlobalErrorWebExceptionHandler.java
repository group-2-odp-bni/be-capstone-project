package com.bni.orange.api.gateway.exception;

import com.bni.orange.api.gateway.model.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        var response = exchange.getResponse();
        var request = exchange.getRequest();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpStatus status;
        String message;
        String errorCode;
        String errorMessage;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            errorCode = status != null ? status.name() : "GATEWAY_ERROR";
            errorMessage = rse.getReason() != null ? rse.getReason() : ex.getMessage();
            message = "Gateway request failed";
        } else if (ex instanceof org.springframework.web.server.ServerWebInputException) {
            status = HttpStatus.BAD_REQUEST;
            errorCode = "INVALID_REQUEST";
            errorMessage = "Invalid request format";
            message = "Request validation failed";
        } else if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
            errorCode = "ACCESS_DENIED";
            errorMessage = "You do not have permission to access this resource";
            message = "Access denied";
        } else if (ex instanceof org.springframework.security.core.AuthenticationException) {
            status = HttpStatus.UNAUTHORIZED;
            errorCode = "AUTHENTICATION_FAILED";
            errorMessage = "Authentication failed";
            message = "Authentication required";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errorCode = "INTERNAL_ERROR";
            errorMessage = "An unexpected error occurred";
            message = "Internal server error";
            log.error("Unhandled exception in API Gateway", ex);
        }

        response.setStatusCode(status);

        var apiResponse = ApiResponse.<Void>builder()
            .message(message)
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode)
                .message(errorMessage)
                .build())
            .path(request.getURI().getPath())
            .build();

        try {
            var json = objectMapper.writeValueAsString(apiResponse);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes());
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            var fallbackJson = String.format(
                "{\"message\":\"%s\",\"error\":{\"code\":\"%s\",\"message\":\"%s\"},\"timestamp\":\"%s\",\"path\":\"%s\"}",
                message,
                errorCode,
                errorMessage,
                java.time.Instant.now(),
                request.getURI().getPath()
            );
            DataBuffer buffer = response.bufferFactory().wrap(fallbackJson.getBytes());
            return response.writeWith(Mono.just(buffer));
        }
    }
}
