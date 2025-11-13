package com.bni.orange.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WahaWebhookEvent(
    String event,
    String session,
    Map<String, Object> payload,
    Object environment,  // Can be String or Map depending on WAHA version
    @JsonProperty("engine")
    String engine
) {
}
