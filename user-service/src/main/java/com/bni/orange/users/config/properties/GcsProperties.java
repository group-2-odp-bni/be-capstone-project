package com.bni.orange.users.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orange.gcp.storage")
public record GcsProperties(
    @NotBlank
    @DefaultValue("orange-wallet-users-profiles")
    String bucketName,

    String projectId,

    @Min(1)
    @DefaultValue("60")
    Integer signedUrlDurationMinutes,

    @DefaultValue("orange-wallet-storage@orange-wallet-project.iam.gserviceaccount.com")
    String serviceAccountEmail
) {
}
