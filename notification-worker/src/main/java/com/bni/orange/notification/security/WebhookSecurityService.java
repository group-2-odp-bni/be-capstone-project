package com.bni.orange.notification.security;

import com.bni.orange.notification.config.properties.WebhookConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSecurityService {

    private final WebhookConfigProperties webhookConfig;

    public boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Missing webhook signature");
            return false;
        }

        try {
            var expectedSignature = calculateHmacSha512(payload);

            var normalizedSignature = signature.startsWith("sha512=") ? signature : "sha512=" + signature;

            var isValid = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                normalizedSignature.getBytes(StandardCharsets.UTF_8)
            );

            if (!isValid) {
                log.warn("Invalid webhook signature. Expected: {}, Received: {} (normalized: {})",
                    maskSignature(expectedSignature),
                    maskSignature(signature),
                    maskSignature(normalizedSignature)
                );
            } else {
                log.debug("Webhook signature verified successfully");
            }

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    private String calculateHmacSha512(String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        var hmacSha512 = Mac.getInstance("HmacSHA512");
        var secretKey = new SecretKeySpec(
            webhookConfig.hmacSecret().getBytes(StandardCharsets.UTF_8),
            "HmacSHA512"
        );
        hmacSha512.init(secretKey);

        var hash = hmacSha512.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha512=" + bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        var result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String maskSignature(String signature) {
        if (signature == null || signature.length() < 20) {
            return "***";
        }
        return signature.substring(0, 8) + "..." + signature.substring(signature.length() - 8);
    }
}
