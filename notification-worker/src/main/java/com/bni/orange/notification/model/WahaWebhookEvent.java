package com.bni.orange.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WahaWebhookEvent(
    String event,
    String session,
    Map<String, Object> payload,
    String environment,
    @JsonProperty("engine")
    String engine
) {
}
