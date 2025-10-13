package com.bni.orange.api.gateway.util;

import com.bni.orange.api.gateway.model.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public final class GatewayResponseUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private GatewayResponseUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Mono<Void> writeErrorResponse(
        ServerWebExchange exchange,
        HttpStatus status,
        String message,
        String errorCode,
        String errorMessage
    ) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        var apiResponse = ApiResponse.<Void>builder()
            .message(message)
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode)
                .message(errorMessage)
                .build())
            .path(exchange.getRequest().getURI().getPath())
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
                exchange.getRequest().getURI().getPath()
            );
            DataBuffer buffer = response.bufferFactory().wrap(fallbackJson.getBytes());
            return response.writeWith(Mono.just(buffer));
        }
    }
}
