package com.bni.orange.notification.service;

import com.bni.orange.authentication.proto.OtpNotificationEvent;
import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.dto.WahaMessageResponse;
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

        String message = formatOtpMessage(event.getOtpCode());

        return wahaSessionService.waitForSessionReady(5, 3)
            .doOnSuccess(session -> log.info("WhatsApp session ready for user {}", event.getUserId()))
            .flatMap(session -> wahaApiClient.sendTextMessage(event.getPhoneNumber(), message))
            .doOnSuccess(response -> log.info("OTP successfully sent to user {}. Message ID: {}, Timestamp: {}",
                event.getUserId(),
                response.id(),
                Instant.ofEpochSecond(response.timestamp())
            ))
            .doOnError(error -> log.error("Failed to send OTP to user {}: {}",
                event.getUserId(),
                error.getMessage()
            ))
            .timeout(Duration.ofSeconds(35));
    }

    private String formatOtpMessage(String otpCode) {
        return """
            BNI Orange E-Wallet

            Your One-Time Password (OTP) is: %s

            This code will expire in 5 minutes.
            Please do not share this code with anyone.

            If you did not request this code, please ignore this message.
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