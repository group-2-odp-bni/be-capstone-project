package com.bni.orange.transaction.model.response.internal;

import lombok.Builder;

import java.util.Map;

@Builder
public record ValidationResultResponse(
    boolean allowed,
    String code,
    String message,
    Map<String, Object> extras
) {}
