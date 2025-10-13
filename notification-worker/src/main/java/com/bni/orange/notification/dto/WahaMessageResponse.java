package com.bni.orange.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WahaMessageResponse(
    String id,
    Long timestamp,
    String from,
    String to,
    String body
) {
}
