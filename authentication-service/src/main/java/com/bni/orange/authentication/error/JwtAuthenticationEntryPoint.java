package com.bni.orange.authentication.error;

import com.bni.orange.authentication.model.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        log.error("Authentication error occurred: {}", authException.getMessage());

        var errorCode = ErrorCode.FORBIDDEN_ACCESS;
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        var apiResponse = ApiResponse.<Void>builder()
            .message("Authentication failed")
            .error(ApiResponse.ErrorDetail.builder()
                .code(errorCode.getCode())
                .message(authException.getMessage())
                .build())
            .path(request.getRequestURI())
            .build();

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
