package com.bni.orange.wallet.service.invite.impl;

import com.bni.orange.wallet.exception.business.ValidationFailedException;
import com.bni.orange.wallet.model.entity.WalletMember;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.request.invite.GeneratedInvite;
import com.bni.orange.wallet.model.response.invite.InviteInspectResponse;
import com.bni.orange.wallet.model.response.member.MemberActionResultResponse;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.service.invite.InviteService;
import com.bni.orange.wallet.service.invite.InviteSession;
import com.bni.orange.wallet.utils.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

  private final StringRedisTemplate redis;
  private final ObjectMapper om;

  private final WalletMemberRepository memberRepo;          
  private final WalletMemberReadRepository memberReadRepo;  

  @Value("${spring.security.invite.secret}")
  String inviteSecret;

  @Value("${app.invite.ttl-seconds:600}")
  long ttlSeconds;

  @Value("${app.invite.base-url:https://app.example.com/invites/claim}")
  String baseUrl;

  private static final String KEY_FMT = "wallet:invite:%s:%s:%s";

  @Override
  public GeneratedInvite generateInviteLink(UUID walletId, UUID userId, String phoneE164, WalletMemberRole role) {
    if (userId != null) throw new ValidationFailedException("Phone-only invite: userId must be null");

    String code  = genCode6();
    String nonce = UUID.randomUUID().toString().replace("-", "");
    String codeHash = hmacSha256Hex(code, inviteSecret);

    var session = InviteSession.builder()
        .walletId(walletId)
        .userId(null)
        .phone(phoneE164)
        .role(role.name())
        .codeHash(codeHash)
        .nonce(nonce)
        .attempts(0)
        .maxAttempts(5)
        .status("INVITED")
        .createdAt(OffsetDateTime.now())
        .build();

    var key = key(walletId, null, nonce);
    try {
      redis.opsForValue().set(key, om.writeValueAsString(session), Duration.ofSeconds(ttlSeconds));
    } catch (Exception e) {
      throw new RuntimeException("Failed to persist invite session", e);
    }

    String token = signToken(walletId, null, nonce);
    String link = baseUrl + "?token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);

    return GeneratedInvite.builder()
        .phoneMasked(mask(phoneE164))
        .link(link)
        .code(code)
        .build();
  }

  @Override
  public InviteInspectResponse inspect(String token) {
    try {
      var claims   = parseAndValidate(token);
      var walletId = UUID.fromString((String) claims.get("wid"));
      var uidStr   = (String) claims.get("uid");
      var nonce    = (String) claims.get("n");

      var session = getSessionFlexible(walletId, uidStr, nonce);
      if (session == null) {
        return InviteInspectResponse.builder()
            .status("EXPIRED")
            .walletId(walletId)
            .expiresAt(claims.getExpiration().toInstant().atOffset(ZoneOffset.UTC))
            .requiresAccount(true)
            .build();
      }

      return InviteInspectResponse.builder()
          .status("VALID")
          .walletId(walletId)
          .role(session.getRole())
          .phoneMasked(mask(session.getPhone()))
          .expiresAt(claims.getExpiration().toInstant().atOffset(ZoneOffset.UTC))
          .requiresAccount(true)
          .build();
    } catch (ValidationFailedException ex) {
      return InviteInspectResponse.builder().status("EXPIRED").build();
    }
  }

  @Override
  @Transactional
  public MemberActionResultResponse acceptToken(String token) {
    var claims   = parseAndValidate(token);
    var walletId = UUID.fromString((String) claims.get("wid"));
    var uidStr   = (String) claims.get("uid");
    var nonce    = (String) claims.get("n");

    var currentUserId = CurrentUser.userId();

    var session = getSessionFlexible(walletId, uidStr, nonce);
    if (session == null) {
      var existing = memberRepo.findByWalletIdAndUserId(walletId, currentUserId);
      if (existing.isPresent() && existing.get().getStatus() == WalletMemberStatus.ACTIVE) {
        return MemberActionResultResponse.builder()
            .walletId(walletId)
            .userId(currentUserId)
            .statusAfter(WalletMemberStatus.ACTIVE)
            .occurredAt(OffsetDateTime.now())
            .message("Already a member")
            .build();
      }
      throw new ValidationFailedException("Invite expired or invalid");
    }


    var opt = memberRepo.findByWalletIdAndUserId(walletId, currentUserId);
    WalletMember member;
    if (opt.isEmpty()) {
      member = WalletMember.builder()
          .walletId(walletId)
          .userId(currentUserId)
          .role(WalletMemberRole.valueOf(session.getRole()))
          .status(WalletMemberStatus.ACTIVE)
          .updatedAt(OffsetDateTime.now())
          .build();
    } else {
      member = opt.get();
      if (member.getStatus() == WalletMemberStatus.INVITED) {
        member.setStatus(WalletMemberStatus.ACTIVE);
      }
      member.setUpdatedAt(OffsetDateTime.now());
    }

    member = memberRepo.save(member);
    upsertRead(member);              

    complete(walletId, currentUserId, nonce);

    return MemberActionResultResponse.builder()
        .walletId(walletId)
        .userId(currentUserId)
        .statusAfter(WalletMemberStatus.ACTIVE)
        .occurredAt(OffsetDateTime.now())
        .message("Invite accepted")
        .build();
  }

  private String key(UUID wid, UUID uidOrNull, String nonce) {
    String uidPart = (uidOrNull == null) ? "-" : uidOrNull.toString();
    return String.format(KEY_FMT, wid, uidPart, nonce);
  }

  private InviteSession getSessionFlexible(UUID wid, String uidStrOrNull, String nonce) {
    String uidPart = (uidStrOrNull == null || uidStrOrNull.isBlank()) ? "-" : uidStrOrNull;
    String k = String.format(KEY_FMT, wid, uidPart, nonce);
    var json = redis.opsForValue().get(k);
    if (json == null) return null;
    try {
      return om.readValue(json, InviteSession.class);
    } catch (Exception e) {
      throw new RuntimeException("Corrupted invite session", e);
    }
  }

  private void complete(UUID wid, UUID uid, String nonce) {
    redis.delete(key(wid, uid, nonce));
    redis.delete(String.format(KEY_FMT, wid, "-", nonce)); 
  }

  private String genCode6() {
    var rnd = new java.security.SecureRandom();
    return String.format("%06d", rnd.nextInt(1_000_000));
  }

  private String hmacSha256Hex(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(out.length * 2);
      for (byte b : out) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String signToken(UUID wid, UUID uidOrNull, String nonce) {
    long now = System.currentTimeMillis();
    long exp = now + ttlSeconds * 1000L;
    byte[] key = inviteSecret.getBytes(StandardCharsets.UTF_8);
    var b = Jwts.builder()
        .setSubject("wallet-invite")
        .claim("wid", wid.toString())
        .claim("n", nonce)
        .setIssuedAt(new Date(now))
        .setExpiration(new Date(exp))
        .signWith(Keys.hmacShaKeyFor(key));
    if (uidOrNull != null) b.claim("uid", uidOrNull.toString());
    return b.compact();
  }

  private Claims parseAndValidate(String token) {
    try {
      return Jwts.parserBuilder()
          .setSigningKey(inviteSecret.getBytes(StandardCharsets.UTF_8))
          .build()
          .parseClaimsJws(token)
          .getBody();
    } catch (JwtException e) {
      throw new ValidationFailedException("Invalid or expired token");
    }
  }

  private String mask(String e164) {
    if (e164 == null || e164.length() < 8) return e164;
    return e164.substring(0, 6) + "****" + e164.substring(e164.length() - 2);
  }

  private void upsertRead(WalletMember m) {
    var read = WalletMemberRead.builder()
        .walletId(m.getWalletId())
        .userId(m.getUserId())
        .role(m.getRole())
        .status(m.getStatus())
        .status(m.getStatus())
        .limitCurrency("IDR")
        .dailyLimitRp(m.getDailyLimitRp())
        .monthlyLimitRp(m.getMonthlyLimitRp())
        .updatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt() : OffsetDateTime.now())
        .build();
    memberReadRepo.save(read);
  }
}
