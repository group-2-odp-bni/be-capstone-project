package com.bni.orange.notification.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WahaSessionResponse(
    String name,
    String status,
    WahaSessionConfig config
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WahaSessionConfig(
        Object webhooks,
        Object proxy,
        Boolean noweb
    ) {
    }
}
