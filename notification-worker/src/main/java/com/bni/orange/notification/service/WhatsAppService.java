package com.bni.orange.notification.service;

import com.bni.orange.authentication.proto.OtpNotificationEvent;
import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.model.response.WahaMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final WahaApiClient wahaApiClient;
    private final WahaSessionService wahaSessionService;

    public Mono<WahaMessageResponse> sendOtp(OtpNotificationEvent event) {
        log.info("Preparing to send OTP to user {} via phone {}", event.getUserId(), maskPhoneNumber(event.getPhoneNumber()));

        var message = formatOtpMessage(event.getOtpCode());

        return wahaSessionService.waitForSessionReady(5, 3)
            .doOnSuccess(session -> log.info("WhatsApp session ready for user {}", event.getUserId()))
            .flatMap(session -> wahaApiClient.sendTextMessage(event.getPhoneNumber(), message))
            .doOnSuccess(response -> {
                var timestamp = response.timestamp() != null ? Instant.ofEpochSecond(response.timestamp()) : "N/A";
                log.info("OTP successfully sent to user {}. Message ID: {}, Timestamp: {}",
                    event.getUserId(),
                    response.id(),
                    timestamp
                );
            })
            .doOnError(error -> log.error("Failed to send OTP to user {}: {}",
                event.getUserId(),
                error.getMessage()
            ))
            .timeout(Duration.ofSeconds(35));
    }

    // private String formatOtpMessage(String otpCode) {
    //     return """
    //         🔸 *BNI Orange E-Wallet* 🔸

    //         Hey there! 👋 Your OTP is: *%s*

    //         ⏰ It’s valid for *5 minutes*.
    //         🚫 Don’t share this code with anyone, not even us.

    //         Stay safe and keep your wallet secure 💪
    //         """.formatted(otpCode);
    // }
    private String formatOtpMessage(String otpCode) {
        return """
            🔸 *BNI Orange E-Wallet* 🔸

            Halo! 👋

            Kode OTP Anda adalah: *%s*

            ⏰ Kode ini berlaku selama *5 menit*.
            🔐 Jangan berikan kode ini kepada siapa pun, termasuk petugas BNI.

            Jika Anda tidak meminta OTP ini, abaikan saja pesan ini.
            """.formatted(otpCode);
    }
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        int visibleDigits = 4;
        String visible = phoneNumber.substring(phoneNumber.length() - visibleDigits);
        return "*".repeat(phoneNumber.length() - visibleDigits) + visible;
    }
}