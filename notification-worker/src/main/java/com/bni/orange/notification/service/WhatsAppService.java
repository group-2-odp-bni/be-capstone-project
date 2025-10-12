package com.bni.orange.notification.service;

import com.bni.orange.authentication.proto.OtpNotificationEvent;
import com.bni.orange.notification.client.WahaApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final WahaApiClient wahaApiClient;

    public Mono<Void> sendOtp(OtpNotificationEvent event) {
        log.info("Preparing to send OTP {} to user {}", event.getOtpCode(), event.getUserId());
        var message = String.format("BNI Orange E-Wallet\n\nYour One-Time Password (OTP) is: %s\n\nPlease do not share this code with anyone.", event.getOtpCode());
        return wahaApiClient.sendTextMessage(event.getPhoneNumber(), message);
    }
}