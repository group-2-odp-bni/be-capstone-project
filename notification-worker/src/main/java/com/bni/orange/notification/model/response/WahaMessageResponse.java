package com.bni.orange.notification.model.response;

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
