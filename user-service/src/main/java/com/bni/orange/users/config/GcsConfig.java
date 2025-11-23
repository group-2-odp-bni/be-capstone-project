package com.bni.orange.users.config;

import com.bni.orange.users.config.properties.GcsProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    private final GcsProperties gcsProperties;

    @Bean
    public Storage storage() {
        if (!gcsProperties.enabled()) {
            log.info("GCS is disabled. Returning mock storage instance");
            return null;
        }

        try {
            // Get default credentials from environment (GCE metadata, GOOGLE_APPLICATION_CREDENTIALS, etc.)
            var sourceCredentials = GoogleCredentials.getApplicationDefault();
            log.info("Successfully loaded Application Default Credentials");

            // If service account email is configured, use impersonation for signed URLs
            if (gcsProperties.serviceAccountEmail() != null && !gcsProperties.serviceAccountEmail().isBlank()) {
                log.info("Using ImpersonatedCredentials for service account: {}", gcsProperties.serviceAccountEmail());

                var impersonatedCredentials = ImpersonatedCredentials.create(
                    sourceCredentials,
                    gcsProperties.serviceAccountEmail(),
                    null, // No delegates
                    List.of("https://www.googleapis.com/auth/cloud-platform"),
                    300 // Token lifetime: 5 minutes
                );

                return StorageOptions.newBuilder()
                    .setProjectId(gcsProperties.projectId())
                    .setCredentials(impersonatedCredentials)
                    .build()
                    .getService();
            }

            // Fallback to default credentials (when no impersonation needed)
            log.info("Using default Application Credentials (no impersonation)");
            return StorageOptions.newBuilder()
                .setProjectId(gcsProperties.projectId())
                .setCredentials(sourceCredentials)
                .build()
                .getService();

        } catch (IOException e) {
            log.error("Failed to initialize Google Cloud Storage. Error: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize GCS Storage client", e);
        }
    }
}
