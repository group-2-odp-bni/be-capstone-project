package com.bni.orange.transaction.service.helper;

import com.bni.orange.transaction.config.properties.BniVaProperties;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.request.TopUpCallbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final int MAX_TIMESTAMP_DIFF_MINUTES = 5;
    private final BniVaProperties bniVaProperties;

    public void validateSignature(PaymentProvider provider, TopUpCallbackRequest request, String signature) {
        switch (provider) {
            case BNI_VA -> validateBniSignature(request, signature);
            case MANDIRI_VA -> validateMandiriSignature(request, signature);
            default -> log.warn("Signature validation not implemented for provider: {}", provider);
        }
    }


    private void validateBniSignature(TopUpCallbackRequest request, String providedSignature) {
        try {
            validateTimestamp(request.paymentTimestamp());

            var payload = constructBniPayload(request);

            var expectedSignature = calculateHmacSha256(payload, bniVaProperties.clientSecret());

            if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {

                log.error("Invalid BNI webhook signature. Expected: {}, Got: {}",
                    maskSignature(expectedSignature), maskSignature(providedSignature));
                throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "Invalid webhook signature");
            }

            log.debug("BNI webhook signature validated successfully for VA: {}", request.vaNumber());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating BNI webhook signature", e);
            throw new BusinessException(ErrorCode.SIGNATURE_VALIDATION_ERROR, "Failed to validate webhook signature");
        }
    }

    /**
     * Validate Mandiri VA webhook signature
     * TODO: Implement based on Mandiri's specification
     */
    private void validateMandiriSignature(TopUpCallbackRequest request, String signature) {
        log.warn("Mandiri signature validation not yet implemented");
        // TODO: Implement Mandiri-specific signature validation
    }

    private String constructBniPayload(TopUpCallbackRequest request) {
        return request.vaNumber() +
            request.paidAmount().toPlainString() +
            request.paymentTimestamp() +
            request.paymentReference();
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            var mac = Mac.getInstance(HMAC_SHA256);
            var secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256
            );
            mac.init(secretKeySpec);

            var hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error calculating HMAC signature", e);
            throw new IllegalStateException("Failed to calculate signature", e);
        }
    }

    private void validateTimestamp(String paymentTimestampStr) {
        try {
            var paymentTimestamp = OffsetDateTime.parse(paymentTimestampStr);
            var now = OffsetDateTime.now();
            long minutesDiff = ChronoUnit.MINUTES.between(paymentTimestamp, now);

            if (Math.abs(minutesDiff) > MAX_TIMESTAMP_DIFF_MINUTES) {
                log.error("Webhook timestamp too old or in future. Diff: {} minutes", minutesDiff);
                throw new BusinessException(ErrorCode.INVALID_TIMESTAMP,
                    "Webhook timestamp is invalid (possible replay attack)");
            }
        } catch (Exception e) {
            log.error("Invalid timestamp format: {}", paymentTimestampStr, e);
            throw new BusinessException(ErrorCode.INVALID_TIMESTAMP,
                "Invalid timestamp format");
        }
    }

    private String maskSignature(String signature) {
        if (signature == null || signature.length() <= 16) {
            return "****";
        }
        return signature.substring(0, 8) + "..." + signature.substring(signature.length() - 8);
    }

    public void validateWebhookRequest(
        PaymentProvider provider,
        TopUpCallbackRequest request,
        String signatureHeader
    ) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.error("Missing signature header in webhook request");
            throw new BusinessException(ErrorCode.MISSING_SIGNATURE,
                "Webhook signature header is required");
        }

        validateSignature(provider, request, signatureHeader);
    }
}
