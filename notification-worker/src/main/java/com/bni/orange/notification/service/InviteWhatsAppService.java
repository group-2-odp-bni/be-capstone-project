package com.bni.orange.notification.service;

import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.model.response.WahaMessageResponse;
import com.bni.orange.wallet.proto.WalletInviteLinkGeneratedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteWhatsAppService {

    private final WahaApiClient wahaApiClient;
    private final WahaSessionService wahaSessionService; 
    public Mono<WahaMessageResponse> sendInviteLink(WalletInviteLinkGeneratedEvent event) {
        final String phone = event.getPhoneE164();
        log.info("Preparing link invite to {}", mask(phone));

        String message = formatLinkInviteMessage(event);

        return wahaSessionService.waitForSessionReady(5, 3)
            .doOnSuccess(s -> log.info("WhatsApp session ready"))
            .flatMap(s -> wahaApiClient.sendTextMessage(phone, message))
            .doOnSuccess(this::logOk)
            .doOnError(err -> log.error("Failed link invite to {}: {}", mask(phone), err.getMessage()))
            .timeout(Duration.ofSeconds(35));
    }


    private String formatLinkInviteMessage(WalletInviteLinkGeneratedEvent e) {
        String codeToShow = (e.getCodePlain() != null && !e.getCodePlain().isBlank())
            ? e.getCodePlain()
            : (e.getCodeMasked().isBlank() ? "******" : e.getCodeMasked());

        return """
            🔸 *Undangan BNI Orange E-Wallet* 🔸

            Anda diundang untuk bergabung ke wallet.
            Peran Anda: *%s*

            1) Klik tautan berikut untuk membuka aplikasi:
            %s

            2) Masukkan *Kode Verifikasi*: %s

            Kode berlaku hingga: %s
            (Abaikan pesan ini jika Anda tidak merasa diminta bergabung.)
            """.formatted(
            e.getRole(), e.getLink(), codeToShow, e.getExpiresAt()
        );
    }

    private void logOk(WahaMessageResponse resp) {
        Object ts = resp.timestamp() != null ? Instant.ofEpochSecond(resp.timestamp()) : "N/A";
        log.info("WA sent. id={}, ts={}", resp.id(), ts);
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 8) return "***";
        return phone.substring(0, 6) + "****" + phone.substring(phone.length() - 2);
    }
}