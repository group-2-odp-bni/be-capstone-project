package com.bni.orange.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@Slf4j
@ConfigurationProperties(prefix = "orange.security")
public record SecurityProperties(
    @DefaultValue("true")
    boolean ipBlockingEnabled,

    @DefaultValue("false")
    boolean allowLocalhostBlocking,

    @DefaultValue("10")
    int violationThreshold,

    @DefaultValue("PT5M")
    Duration violationWindow,

    @DefaultValue("PT1H")
    Duration blockDuration,

    @DefaultValue("PT30M")
    Duration suspiciousDuration,

    @DefaultValue("5")
    int suspiciousThreshold
) {

}
