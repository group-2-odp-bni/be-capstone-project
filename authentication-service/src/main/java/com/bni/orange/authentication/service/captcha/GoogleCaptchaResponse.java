package com.bni.orange.authentication.service.captcha;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GoogleCaptchaResponse(
    boolean success,
    @JsonProperty("challenge_ts") String challengeTs,
    String hostname,
    @JsonProperty("error-codes") List<String> errorCodes
) {}
