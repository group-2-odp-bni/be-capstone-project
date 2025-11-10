package com.bni.orange.notification.service;

import com.bni.orange.notification.client.UserClient;
import com.bni.orange.notification.client.WahaApiClient;
import com.bni.orange.notification.model.MemberLink;
import com.bni.orange.notification.model.SplitBillCreatedEvent;
import com.bni.orange.notification.model.SplitBillRemindedEvent;
import com.bni.orange.notification.model.response.UserProfileResponse;
import com.bni.orange.notification.model.response.WahaMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SplitBillWhatsAppService {

  private final WahaApiClient wahaApiClient;
  private final WahaSessionService wahaSessionService;
  private final UserClient userClient;

  @Value("${orange.app.register-url:https://app.orangebybni.my.id/register}")
  private String registerUrl;

  public Mono<Void> sendBillCreated(SplitBillCreatedEvent e) {
    Mono<Void> sendOwner = resolveRecipient(e.getOwnerPhoneE164(), e.getOwnerFullName(), e.getOwnerUserId())
        .flatMap(r -> {
          String msg = r.known()
              ? formatOwnerBillCreatedKnown(e, r)
              : formatOwnerBillCreatedUnknown(e, r);
          return wahaApiClient.sendTextMessage(r.phoneE164(), msg).doOnSuccess(this::logOk);
        })
        .then();

    final int total = e.getMemberLinks() == null ? 0 : e.getMemberLinks().size();
    java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicInteger fail = new java.util.concurrent.atomic.AtomicInteger();

    Mono<Void> sendMembers = Flux.fromIterable(nullSafe(e.getMemberLinks()))
        .index()
        .flatMap(t -> {
          long idx = t.getT1() + 1;
          MemberLink m = t.getT2();
          return resolveRecipient(m.getPhoneE164(), null, String.valueOf(m.getUserId()))
              .flatMap(r -> {
                String msg = r.known()
                    ? formatMemberBillCreatedKnown(e, safe(m.getShortLink()), r)
                    : formatMemberBillCreatedUnknown(e, safe(m.getShortLink()), r);
                long start = System.nanoTime();
                return wahaApiClient.sendTextMessage(r.phoneE164(), msg)
                    .doOnSuccess(resp -> {
                      ok.incrementAndGet();
                      long durMs = (System.nanoTime() - start) / 1_000_000;
                      log.info("[{}/{}] SENT billId={} to={} waId={} in {}ms",
                          idx, total, e.getBillId(), mask(r.phoneE164()), resp.id(), durMs);
                    })
                    .doOnError(err -> {
                      fail.incrementAndGet();
                      log.error("[{}/{}] FAILED billId={} to={} err={}",
                          idx, total, e.getBillId(), mask(r.phoneE164()), err.toString());
                    });
              })
              .onErrorResume(err -> {
                fail.incrementAndGet();
                log.error("[{}/{}] RESOLVE/SEND FAILED billId={} memberId={} err={}",
                    idx, total, e.getBillId(), safe(m.getMemberId()), err.toString());
                return Mono.empty();
              });
        }, 5)
        .then()
        .doFinally(sig -> log.info("SplitBillCreated summary billId={} total={} ok={} fail={} signal={}",
            e.getBillId(), total, ok.get(), fail.get(), sig));

    return wahaSessionService.waitForSessionReady(5, 3)
        .then(sendOwner)
        .then(sendMembers)
        .timeout(Duration.ofSeconds(35));
  }

  public Mono<Void> sendBillReminded(SplitBillRemindedEvent e) {
    Mono<Void> sendMembers = Flux.fromIterable(nullSafe(e.getMemberLinks()))
        .flatMap((MemberLink m) ->
            resolveRecipient(m.getPhoneE164(), null, String.valueOf(m.getUserId()))
              .flatMap(r -> wahaApiClient.sendTextMessage(
                  r.phoneE164(),
                  r.known()
                      ? formatMemberReminderKnown(e, safe(m.getShortLink()), r)
                      : formatMemberReminderUnknown(e, safe(m.getShortLink()), r)
                ).doOnSuccess(this::logOk)
              )
              .onErrorResume(err -> {
                log.error("Resolve/send member failed userId={} err={}", maskUser(String.valueOf(m.getUserId())), err.getMessage());
                return Mono.empty();
              })
        , 5)
        .then();

    Mono<Void> sendActorSummary = resolveRecipient(null, null, e.getRemindedByUserId())
        .flatMap(actor -> wahaApiClient.sendTextMessage(
            actor.phoneE164(), formatRemindedSummaryMessage(e, actor)
          ).doOnSuccess(this::logOk)
        )
        .then();

    return wahaSessionService.waitForSessionReady(5, 3)
        .then(sendMembers)
        .then(sendActorSummary)
        .timeout(Duration.ofSeconds(35));
  }

  private Mono<Recipient> resolveRecipient(String phoneFromEvent, String nameFromEvent, String userId) {
    String phone = normalizeE164(phoneFromEvent);
    if (phone != null && !phone.isBlank()) {
      return userClient.findUserByPhone(phone)
          .map(this::toRecipientKnown)
          .switchIfEmpty(Mono.just(Recipient.unknown(phone, displayNameOrDefault(nameFromEvent))));
    }
    if (userId != null && !userId.isBlank()) {
      return userClient.findUserById(userId).map(this::toRecipientKnown);
    }
    return Mono.error(new IllegalStateException("No phone or userId to resolve recipient"));
  }

  private Recipient toRecipientKnown(UserProfileResponse u) {
    String phone = normalizeE164(u.getPhoneNumber());
    String name  = displayNameOrDefault(u.getFullName());
    return new Recipient(phone, name, true);
  }

  private String displayNameOrDefault(String name) {
    return (name == null || name.isBlank()) ? "Teman" : name;
  }

  private String formatOwnerBillCreatedKnown(SplitBillCreatedEvent e, Recipient r) {
    return """
        🧾 *Split Bill Dibuat!*
        
        Hai %s, tagihan patungan kamu berhasil dibuat.
        • ID Tagihan: *%s*
        • Tautan Owner (kelola & pantau):
        %s
        
        _Dibuat pada: %s_
        """.formatted(r.displayName(), e.getBillId(), e.getOwnerShortLink(), safe(e.getCreatedAt()));
  }

  private String formatOwnerBillCreatedUnknown(SplitBillCreatedEvent e, Recipient r) {
    return """
        🧾 *Split Bill Dibuat!*
        
        Hai %s, kamu baru saja membuat tagihan.
        • ID Tagihan: *%s*
        • Tautan Owner:
        %s
        
        Akunmu belum terdaftar. Daftar dulu agar lebih mudah memantau dan membayar:
        %s
        
        _Dibuat pada: %s_
        """.formatted(r.displayName(), e.getBillId(), e.getOwnerShortLink(), registerUrl, safe(e.getCreatedAt()));
  }

  private String formatMemberBillCreatedKnown(SplitBillCreatedEvent e, String memberShortLink, Recipient r) {
    return """
        🍊 *Ayo Patungan di BNI Orange!*
        
        Hai %s, kamu diundang ikut split bill.
        • ID Tagihan: *%s*
        
        Klik tautan pribadimu untuk cek rincian & bayar jatahmu:
        %s
        """.formatted(r.displayName(), e.getBillId(), memberShortLink);
  }

  private String formatMemberBillCreatedUnknown(SplitBillCreatedEvent e, String memberShortLink, Recipient r) {
    return """
        🍊 *Ayo Patungan di BNI Orange!*
        
        Hai %s, kamu diundang ikut split bill.
        • ID Tagihan: *%s*
        
        Tautan pribadimu:
        %s
        
        Akunmu belum terdaftar. Daftar dulu supaya pembayaran lebih mudah:
        %s
        """.formatted(r.displayName(), e.getBillId(), memberShortLink, registerUrl);
  }

  private String formatMemberReminderKnown(SplitBillRemindedEvent e, String memberShortLink, Recipient r) {
    return """
        🔔 *Pengingat Pembayaran Split Bill*
        
        Hai %s, kamu masih punya tagihan di *%s*.
        Silakan cek rincian & bayar lewat tautan pribadimu:
        %s
        """.formatted(r.displayName(), safe(e.getBillId()), memberShortLink);
  }

  private String formatMemberReminderUnknown(SplitBillRemindedEvent e, String memberShortLink, Recipient r) {
    return """
        🔔 *Pengingat Pembayaran Split Bill*
        
        Hai %s, kamu masih punya tagihan di *%s*.
        Tautan pribadimu:
        %s
        
        Supaya proses lebih mudah, daftar dulu:
        %s
        """.formatted(r.displayName(), safe(e.getBillId()), memberShortLink, registerUrl);
  }

  private String formatRemindedSummaryMessage(SplitBillRemindedEvent e, Recipient actor) {
    String result = summarizeResult(e);
    return """
        🔔 *Pengingat Split Bill*
        
        Hai %s, ringkasan pengingat untuk tagihan *%s*.
        Hasil: %s
        
        _Oleh: %s • Kanal: %s_
        """.formatted(
          actor.displayName(),
          safe(e.getBillId()),
          result,
          maskUser(e.getRemindedByUserId()),
          safe(e.getRequestedChannels() == null ? "-" : String.join(",", e.getRequestedChannels()))
        );
  }

  private <T> List<T> nullSafe(List<T> v) { return v == null ? List.of() : v; }
  private String summarizeResult(SplitBillRemindedEvent e) {
    if (e.getResult() == null || e.getResult().isEmpty()) return "-";
    var sb = new StringBuilder();
    e.getResult().forEach((k, v) -> {
      if (v instanceof Map<?, ?> m) {
        Object s = m.get("success"); Object f = m.get("fail");
        if (sb.length() > 0) sb.append(" • ");
        sb.append(k).append(": ").append(s).append(" ok/").append(f).append(" gagal");
      } else {
        if (sb.length() > 0) sb.append(" • ");
        sb.append(k).append(": ").append(String.valueOf(v));
      }
    });
    return sb.toString();
  }

  private String normalizeE164(String phone) {
    if (phone == null) return null;
    var p = phone.trim();
    if (p.isEmpty()) return null;
    if (p.startsWith("0")) p = "+62" + p.substring(1);
    if (!p.startsWith("+")) p = "+" + p;
    return p;
  }

  private void logOk(WahaMessageResponse resp) {
    Object ts = (resp.timestamp() != null) ? Instant.ofEpochSecond(resp.timestamp()) : "N/A";
    log.info("WA sent. id={}, ts={}", resp.id(), ts);
  }

  private String safe(String v) { return (v == null || v.isBlank()) ? "-" : v; }
  private String mask(String phone) { return (phone == null || phone.length() < 8) ? "***" : phone.substring(0,6) + "****" + phone.substring(phone.length()-2); }
  private String maskUser(String userId) { return (userId == null || userId.length()<6) ? "***" : userId.substring(0,3) + "***" + userId.substring(userId.length()-2); }

  private record Recipient(String phoneE164, String displayName, boolean known) {
    static Recipient unknown(String phoneE164, String displayName) { return new Recipient(phoneE164, displayName, false); }
  }
}
