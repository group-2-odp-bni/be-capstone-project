package com.bni.orange.notification.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WahaQRCodeResponse(
    String value,
    String base64
) {
}
