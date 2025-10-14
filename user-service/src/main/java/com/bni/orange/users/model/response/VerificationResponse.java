package com.bni.orange.users.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationResponse {

    private Boolean verified;
    private String message;
    private String field;
    private String newValue;

    public static VerificationResponse success(String field, String newValue) {
        return VerificationResponse.builder()
            .verified(true)
            .message("Verification successful")
            .field(field)
            .newValue(newValue)
            .build();
    }
}
