package com.bni.orange.authentication.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GoogleCaptchaResponse(
    boolean success,
    double score,
    String action,

    @JsonProperty("challenge_ts")
    String challengeTs,

    String hostname,

    @JsonProperty("error-codes")
    List<String> errorCodes
) {}
