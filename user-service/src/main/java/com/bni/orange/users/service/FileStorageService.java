package com.bni.orange.users.service;

import com.bni.orange.users.config.properties.GcsProperties;
import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final Storage storage;
    private final GcsProperties gcsProperties;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    );

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2 MB

    /**
     * Upload profile image to GCS and return the GCS path (not signed URL).
     * The path will be stored in database and signed URL will be generated on-demand.
     *
     * @param file MultipartFile to upload
     * @param userId User ID for file naming
     * @return GCS path (e.g., "profiles/userId.jpg")
     */
    public String uploadProfileImage(MultipartFile file, UUID userId) {
        validateFile(file);

        var fileName = buildFileName(userId, file.getOriginalFilename());
        var contentType = file.getContentType();

        try {
            var blobId = BlobId.of(gcsProperties.bucketName(), fileName);
            var blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

            storage.create(blobInfo, file.getBytes());

            log.info("Successfully uploaded profile image for user: {} to GCS path: {}", userId, fileName);

            return fileName;
        } catch (IOException e) {
            log.error("Failed to upload profile image for user: {}", userId, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "Failed to upload profile image");
        }
    }

    /**
     * Delete profile image from GCS.
     *
     * @param gcsPath GCS path of the image to delete (e.g., "profiles/userId.jpg")
     */
    public void deleteProfileImage(String gcsPath) {
        if (gcsPath == null || gcsPath.isBlank()) {
            return;
        }

        try {
            var blobId = BlobId.of(gcsProperties.bucketName(), gcsPath);
            var deleted = storage.delete(blobId);

            if (deleted) {
                log.info("Successfully deleted profile image at GCS path: {}", gcsPath);
            } else {
                log.warn("Profile image not found for deletion at GCS path: {}", gcsPath);
            }
        } catch (Exception e) {
            log.error("Failed to delete profile image at GCS path: {}", gcsPath, e);
            // Don't throw exception - deletion failure shouldn't block upload
        }
    }


    /**
     * Generate signed URL from GCS path.
     * This is called on-demand when retrieving user profile to provide fresh, valid URLs.
     *
     * @param gcsPath GCS path (e.g., "profiles/userId.jpg")
     * @return Signed URL with 60-minute expiry
     */
    public String generateSignedUrl(String gcsPath) {
        try {
            var blobInfo = BlobInfo.newBuilder(gcsProperties.bucketName(), gcsPath).build();

            var sourceCredentials = GoogleCredentials.getApplicationDefault();
            var impersonatedCredentials = ImpersonatedCredentials.create(
                sourceCredentials,
                gcsProperties.serviceAccountEmail(),
                null,
                Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"),
                300
            );

            var url = storage.signUrl(
                blobInfo,
                gcsProperties.signedUrlDurationMinutes(),
                TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                Storage.SignUrlOption.signWith(impersonatedCredentials)
            );

            log.debug("Successfully generated signed URL for GCS path: {} using impersonated service account: {}",
                gcsPath, gcsProperties.serviceAccountEmail());
            return url.toString();

        } catch (Exception e) {
            log.error("Failed to generate signed URL for GCS path: {}. " +
                "Error: {}. " +
                "Troubleshooting steps:\n" +
                "1) Run: gcloud auth application-default login\n" +
                "2) Grant impersonation permission:\n" +
                "   gcloud iam service-accounts add-iam-policy-binding {} --member=\"user:YOUR_EMAIL\" --role=\"roles/iam.serviceAccountTokenCreator\"\n" +
                "3) Verify service account exists and has Storage Object Admin role",
                gcsPath, e.getMessage(), gcsProperties.serviceAccountEmail(), e);

            throw new BusinessException(
                ErrorCode.FILE_URL_GENERATION_FAILED,
                "Failed to generate signed URL. Check IAM impersonation permissions."
            );
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE, "File is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(
                ErrorCode.FILE_TOO_LARGE,
                String.format("File size exceeds maximum allowed size of %d MB", MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        var contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(
                ErrorCode.INVALID_FILE_TYPE,
                "Invalid file type. Allowed types: JPEG, PNG, WebP"
            );
        }
    }

    private String buildFileName(UUID userId, String originalFilename) {
        var extension = getFileExtension(originalFilename);
        return String.format("profiles/%s.%s", userId, extension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
