package com.bni.orange.api.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "orange.cors")
public record CorsProperties(
    List<String> allowedOrigins
) {
}