package com.bni.orange.wallet.model.response.internal;

import java.util.Map;

public record RoleValidateResponse(
    boolean allowed,
    String code,
    String message,
    String effectiveRole,
    Map<String, Object> extras
) {}
