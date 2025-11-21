package com.bni.orange.users.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileImageUploadResponse {

    /**
     * Signed URL to access the uploaded profile image.
     * Note: This URL expires after the specified duration.
     * The GCS path (not this signed URL) is stored in the database.
     */
    private String profileImageUrl;
    private String message;
    private Long urlExpiresInMinutes;

    public static ProfileImageUploadResponse success(String signedUrl, Long expiresInMinutes) {
        return ProfileImageUploadResponse.builder()
            .profileImageUrl(signedUrl)
            .message("Profile image uploaded successfully")
            .urlExpiresInMinutes(expiresInMinutes)
            .build();
    }
}
