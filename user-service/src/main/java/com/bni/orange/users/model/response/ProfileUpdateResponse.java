package com.bni.orange.users.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileUpdateResponse {

    private String message;
    private List<String> updatedFields;
    private Map<String, PendingVerification> pendingVerifications;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PendingVerification {
        private String field;
        private String value;
        private Boolean otpSent;
        private Long expiresInSeconds;
        private String verifyEndpoint;
    }

    public boolean hasPendingVerifications() {
        return pendingVerifications != null && !pendingVerifications.isEmpty();
    }
}
