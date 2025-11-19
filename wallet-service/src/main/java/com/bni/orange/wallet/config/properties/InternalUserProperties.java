package com.bni.orange.wallet.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.internal-user")
public class InternalUserProperties {

    @NotBlank(message = "Internal user service base URL must not be blank")
    private String baseUrl;

    @Min(value = 1000, message = "Connect timeout must be at least 1000ms")
    @Max(value = 30000, message = "Connect timeout must not exceed 30000ms")
    private int connectTimeout = 5000;

    @Min(value = 1, message = "Read timeout must be at least 1 second")
    @Max(value = 60, message = "Read timeout must not exceed 60 seconds")
    private int readTimeout = 5;

    @Min(value = 1, message = "Write timeout must be at least 1 second")
    @Max(value = 60, message = "Write timeout must not exceed 60 seconds")
    private int writeTimeout = 5;

    @Min(value = 1, message = "Max connections must be at least 1")
    @Max(value = 500, message = "Max connections must not exceed 500")
    private int maxConnections = 100;

    @Min(value = 1, message = "Pending acquire timeout must be at least 1 second")
    @Max(value = 300, message = "Pending acquire timeout must not exceed 300 seconds")
    private int pendingAcquireTimeout = 45;
}
