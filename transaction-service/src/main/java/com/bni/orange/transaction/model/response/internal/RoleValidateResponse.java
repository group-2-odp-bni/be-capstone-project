package com.bni.orange.transaction.model.response.internal;

import lombok.Builder;

import java.util.Map;

@Builder
public record RoleValidateResponse(
    boolean allowed,
    String code,
    String message,
    String effectiveRole,
    Map<String, Object> extras
) {}
