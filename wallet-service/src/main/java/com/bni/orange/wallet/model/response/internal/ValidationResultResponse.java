package com.bni.orange.wallet.model.response.internal;

import java.util.Map;

public record ValidationResultResponse(
    boolean allowed,
    String code,
    String message,
    Map<String, Object> extras
) {}
