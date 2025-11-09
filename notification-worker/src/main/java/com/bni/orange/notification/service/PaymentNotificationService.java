package com.bni.orange.notification.service;

import com.bni.orange.notification.client.UserClient;
import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.model.PaymentIntentCreatedEvent;
import com.bni.orange.notification.model.PaymentStatusUpdatedEvent;
import com.bni.orange.notification.model.response.UserProfileResponse;
import com.bni.orange.notification.model.response.WahaMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotificationService {

  private final WahaApiClient wahaApiClient;
  private final WahaSessionService wahaSessionService;
  private final UserClient userClient;

  public Mono<Void> sendPaymentIntent(PaymentIntentCreatedEvent e) {
    return wahaSessionService.waitForSessionReady(5, 3)
      .doOnSuccess(s -> log.info("WhatsApp session ready"))
      .then(resolveUserLite(e.getUserId())
        .flatMap(u -> wahaApiClient.sendTextMessage(u.phoneE164(), formatPaymentIntentMessage(e, u))
          .doOnSuccess(this::logOk)
          .doOnError(err -> log.error("Failed payment-intent {}: {}", mask(u.phoneE164()), err.getMessage()))
        )
      )
      .then()
      .timeout(Duration.ofSeconds(35));
  }

  public Mono<Void> sendPaymentStatus(PaymentStatusUpdatedEvent e) {
    // TODO: resolve via service PI→user.
    return Mono.empty();
  }

  private Mono<UserLite> resolveUserLite(String userId) {
    return userClient.findUserById(userId)
      .switchIfEmpty(Mono.error(new IllegalStateException("User not found: " + userId)))
      .map(this::toUserLite)
      .flatMap(u -> {
        if (u.phoneE164() == null || u.phoneE164().isBlank()) {
          return Mono.error(new IllegalStateException("User has no phone: " + userId));
        }
        return Mono.just(u);
      });
  }

  private UserLite toUserLite(UserProfileResponse u) {
    String phone = normalizeE164(u.getPhoneNumber());
    String name  = (u.getFullName() == null || u.getFullName().isBlank()) ? "Nasabah" : u.getFullName();
    return new UserLite(phone, name);
  }

  private String formatPaymentIntentMessage(PaymentIntentCreatedEvent e, UserLite user) {
    String amount = formatIDR(e.getAmountMinor());
    String bill   = (e.getBillId() == null || e.getBillId().isBlank()) ? "" : "\n• ID Tagihan: *" + e.getBillId() + "*";
    return """
        💳 *Payment Intent Dibuat*
        
        Hai %s, kami menyiapkan pembayaran kamu.
        • Nominal: *%s*%s
        • Mata Uang: %s
        
        Silakan selesaikan pembayaranmu. Jika ini bukan kamu, abaikan pesan ini.
        
        _Dibuat: %s_
        """.formatted(
        safeName(user.displayName()), amount, bill,
        safeStr(e.getCurrency(), "IDR"), safeStr(e.getCreatedAt(), "-")
    );
  }
  private String formatIDR(Long minor) {
    if (minor == null) return "Rp 0";
    double major = minor / 100.0; 
    NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    return nf.format(major);
  }

  private String normalizeE164(String phone) {
    if (phone == null) return null;
    var p = phone.trim();
    if (p.startsWith("0")) p = "+62" + p.substring(1);
    if (!p.startsWith("+")) p = "+" + p;
    return p;
  }

  private void logOk(WahaMessageResponse resp) {
    Object ts = (resp.timestamp() != null) ? Instant.ofEpochSecond(resp.timestamp()) : "N/A";
    log.info("WA sent. id={}, ts={}", resp.id(), ts);
  }

  private String safeStr(String s, String fb) { return (s == null || s.isBlank()) ? fb : s; }
  private String safeName(String s) { return (s == null || s.isBlank()) ? "Nasabah" : s; }
  private String mask(String phone) { return (phone == null || phone.length() < 8) ? "***" : phone.substring(0,6) + "****" + phone.substring(phone.length()-2); }

  private record UserLite(String phoneE164, String displayName) {}
}
