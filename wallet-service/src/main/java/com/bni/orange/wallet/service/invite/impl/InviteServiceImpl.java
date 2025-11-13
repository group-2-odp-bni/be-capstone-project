package com.bni.orange.wallet.service.invite.impl;

import com.bni.orange.wallet.exception.business.ConflictException;
import com.bni.orange.wallet.exception.business.ForbiddenOperationException;
import com.bni.orange.wallet.exception.business.MaxMemberReachException;
import com.bni.orange.wallet.exception.business.ValidationFailedException;
import com.bni.orange.wallet.model.entity.WalletMember;
import com.bni.orange.wallet.model.entity.read.UserWalletRead;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import com.bni.orange.wallet.model.request.invite.GeneratedInvite;
import com.bni.orange.wallet.model.response.invite.InviteInspectResponse;
import com.bni.orange.wallet.model.response.invite.VerifyInviteCodeResponse;
import com.bni.orange.wallet.model.response.member.MemberActionResultResponse;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.service.invite.InviteService;
import com.bni.orange.wallet.service.invite.InviteSession;
import com.bni.orange.wallet.service.query.impl.WalletPolicyQueryServiceImpl;
import com.bni.orange.wallet.utils.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bni.orange.wallet.domain.DomainEvents.WalletInviteLinkGenerated;
import com.bni.orange.wallet.domain.DomainEvents.WalletInviteAccepted;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;

import org.springframework.util.StringUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

  private final StringRedisTemplate redis;
  private final ObjectMapper om;
  private final WalletPolicyQueryServiceImpl walletPolicyService;

  private final WalletMemberRepository memberRepo;          
  private final WalletMemberReadRepository memberReadRepo;  
  private final com.bni.orange.wallet.repository.read.WalletReadRepository walletReadRepo;
  private final com.bni.orange.wallet.repository.WalletRepository walletRepo;
  private final com.bni.orange.wallet.repository.read.UserWalletReadRepository userWalletReadRepo;
  private final ApplicationEventPublisher appEvents;

  @Value("${spring.security.invite.secret}")
  String inviteSecret;

  @Value("${app.invite.ttl-seconds:600}")
  long ttlSeconds;

  @Value("${app.invite.base-url}")
  String baseUrl;

  private static final String KEY_FMT = "wallet:invite:%s:%s:%s";
  private static final String INDEX_KEY_FMT  = "wallet:invite:index:%s:%s";
  @Override
  @Transactional
  public GeneratedInvite generateInviteLink(UUID walletId, UUID userId, String phoneE164, WalletMemberRole role) {
    requireAdminOrOwner(walletId);
    if (userId == null) {
        if (!StringUtils.hasText(phoneE164)) {
            throw new ValidationFailedException("userId or phoneE164 is required");
        }
    } 
    if (userId != null && !StringUtils.hasText(phoneE164)) {
        throw new ValidationFailedException("phoneE164 is required when userId is specified for this flow");
    }
    if (role == null) {
      throw new ValidationFailedException("Role is required");
    }
    if (role == WalletMemberRole.OWNER) throw new ForbiddenOperationException("Cannot invite as OWNER");
    var policy = walletPolicyService.getWalletPolicy(walletId);
    if (!"SHARED".equalsIgnoreCase(policy.getWalletType())) {
      throw new ForbiddenOperationException("Inviting members is only allowed for SHARED wallets");
    }
    long currentMembers = memberRepo.countByWalletIdAndStatusIn(
        walletId, List.of(WalletMemberStatus.ACTIVE, WalletMemberStatus.INVITED)
    );
    if (currentMembers >= policy.getMaxMembers()) {
      throw new MaxMemberReachException("MAX_MEMBERS_REACHED");
    }
    walletRepo.findById(walletId).orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    String conflictIndexKey;
    String keyForConflictValue; // Nilai yang digunakan untuk format KEY_FMT
    
    if (userId != null) {
        keyForConflictValue = userId.toString();
        conflictIndexKey = String.format("wallet:invite:index:user:%s:%s", walletId, userId); 
    } else {
        keyForConflictValue = phoneE164;
        conflictIndexKey = String.format(INDEX_KEY_FMT, walletId, phoneE164);
    }
    String nonce = UUID.randomUUID().toString().replace("-", "");
    Boolean locked = redis.opsForValue().setIfAbsent(conflictIndexKey, nonce, Duration.ofSeconds(ttlSeconds));      
    if (Boolean.FALSE.equals(locked)) {
        String existingNonce = redis.opsForValue().get(conflictIndexKey);        
        String existingJson = (existingNonce != null)
              ? redis.opsForValue().get(String.format(KEY_FMT, walletId, 
                  (userId != null ? userId.toString() : "-"), 
                  existingNonce))
              : null;

        if (existingJson != null) {
            try {
                InviteSession exist = om.readValue(existingJson, InviteSession.class);
                OffsetDateTime expiresAt = exist.getCreatedAt().plusSeconds(ttlSeconds);
                throw new ConflictException("USER_ALREADY_INVITED (expires at: " + expiresAt + ")");
            } catch (Exception ignore) {
                throw new ConflictException("USER_ALREADY_INVITED");
            }
        }
        throw new ConflictException("USER_ALREADY_INVITED");
    }

    String code  = genCode6();
    String codeHash = hmacSha256Hex(code, inviteSecret);
    var createdAt = OffsetDateTime.now();

    var session = InviteSession.builder()
        .walletId(walletId)
        .userId(userId)
        .phone(phoneE164)
        .role(role.name())
        .codeHash(codeHash)
        .nonce(nonce)
        .attempts(0)
        .maxAttempts(5)
        .status(userId != null ? "BOUND" : "INVITED")
        .createdAt(createdAt)
        .build();

    var key = key(walletId, userId, nonce);
    try {
        redis.opsForValue().set(key, om.writeValueAsString(session), Duration.ofSeconds(ttlSeconds));
    } catch (Exception e) {
        redis.delete(conflictIndexKey);
        throw new RuntimeException("Failed to persist invite session", e);
    }
    String token = signToken(walletId, userId, nonce);
    String link = baseUrl + "?token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
    var inviter = CurrentUser.userId();
    var expiresAt = createdAt.plusSeconds(ttlSeconds);
    appEvents.publishEvent(WalletInviteLinkGenerated.builder()
            .walletId(walletId)
            .inviterUserId(inviter)
            .phoneE164(phoneE164)
            .role(role)
            .link(link)
            .codeMasked(mask(code))
            .codePlain(code) 
            .expiresAt(expiresAt)
            .nonce(nonce)
            .build()
    );
    return GeneratedInvite.builder()
        .phoneMasked(mask(phoneE164))
        .link(link)
        .code(code)
        .build();
  }
  private WalletMember requireAdminOrOwner(UUID walletId) {
    var uid = CurrentUser.userId();
    var me = memberRepo.findByWalletIdAndUserId(walletId, uid)
        .orElseThrow(() -> new ForbiddenOperationException("You are not a member of this wallet"));

    if (me.getStatus() != WalletMemberStatus.ACTIVE) {
      throw new ForbiddenOperationException("Only ACTIVE members can perform this action");
    }
    if (me.getRole() != WalletMemberRole.OWNER && me.getRole() != WalletMemberRole.ADMIN) {
      throw new ForbiddenOperationException("Only OWNER/ADMIN can perform this action");
    }
    return me;
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
      boolean isVerified = "VERIFIED".equalsIgnoreCase(session.getStatus()) || "BOUND".equalsIgnoreCase(session.getStatus());
      return InviteInspectResponse.builder()
          .status(isVerified ? "VERIFIED" : "VALID")          
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
  public VerifyInviteCodeResponse verifyCode(String token, String code) {
    if (!StringUtils.hasText(token) || !StringUtils.hasText(code)) {
      throw new ValidationFailedException("Token and code are required");
    }
    var initialClaims = parseAndValidate(token);
    if (initialClaims.containsKey("uid")) {
        throw new ForbiddenOperationException("Invite already verified or bound to a user, please proceed to accept token.");
    }
    var wid      = UUID.fromString((String) initialClaims.get("wid"));
    var nonce    = (String) initialClaims.get("n");
    var expires  = initialClaims.getExpiration().toInstant().atOffset(ZoneOffset.UTC);
    var anonKey = String.format(KEY_FMT, wid, "-", nonce);
    var json = redis.opsForValue().get(anonKey);
    if (json == null) {
      return VerifyInviteCodeResponse.builder()
          .status("EXPIRED").walletId(wid).verified(false).expiresAt(expires)
          .build();
    }

    InviteSession s;
    try { s = om.readValue(json, InviteSession.class); }
    catch (Exception e) { throw new RuntimeException("Corrupted invite session", e); }

    if (s.getAttempts() >= s.getMaxAttempts()) {
      redis.delete(anonKey);
      String indexKey = String.format(INDEX_KEY_FMT, wid, s.getPhone());
      redis.delete(indexKey);
      return VerifyInviteCodeResponse.builder()
          .status("EXPIRED").walletId(wid).verified(false).expiresAt(expires)
          .build();
    }

    var c = code.replaceAll("[^0-9]", "");
    if (c.length() != 6) throw new ValidationFailedException("Invalid code format");

    var ok = hmacSha256Hex(c, inviteSecret).equalsIgnoreCase(s.getCodeHash());
    if (!ok) {
      s.setAttempts((s.getAttempts()) + 1);
      var remain = remainingTtlSeconds(s.getCreatedAt());
      if (remain > 0) redis.opsForValue().set(anonKey, writeJson(s), Duration.ofSeconds(remain));
      else redis.delete(anonKey);

      return VerifyInviteCodeResponse.builder()
          .status("INVALID_CODE")
          .walletId(wid)
          .phoneMasked(mask(s.getPhone()))
          .verified(false)
          .expiresAt(expires)
          .build();
    }

    var uid = CurrentUser.userId();                
    s.setUserId(uid);
    s.setStatus("VERIFIED");
    var boundKey = String.format(KEY_FMT, wid, uid, nonce);
    redis.delete(anonKey);
    var remain = remainingTtlSeconds(s.getCreatedAt());
    if (remain > 0) redis.opsForValue().set(boundKey, writeJson(s), Duration.ofSeconds(remain));

    String boundToken = signToken(wid, uid, nonce);

    return VerifyInviteCodeResponse.builder()
        .status("VERIFIED")
        .walletId(wid)
        .phoneMasked(mask(s.getPhone()))
        .verified(true)
        .expiresAt(expires)
        .boundToken(boundToken)
        .build();
  }
  private String writeJson(Object o) {
    try { return om.writeValueAsString(o); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  private long remainingTtlSeconds(OffsetDateTime createdAt) {
    if (createdAt == null) return 0L;
    var expireAt = createdAt.plusSeconds(ttlSeconds);
    var now = OffsetDateTime.now();
    var remain = java.time.Duration.between(now, expireAt).getSeconds();
    return Math.max(0L, remain);
  }

  @Override
  @Transactional
  public MemberActionResultResponse acceptToken(String token) {
    var claims   = parseAndValidate(token);
    var walletId = UUID.fromString((String) claims.get("wid"));
    var uidStr   = (String) claims.get("uid");
    var nonce    = (String) claims.get("n");

    var currentUserId = CurrentUser.userId();
    InviteSession session = null;
    if (uidStr != null) {
      var uidFromToken = UUID.fromString(uidStr);
      if (!uidFromToken.equals(currentUserId)) {
        throw new ValidationFailedException("Invite is not verified for this account");
      }
      session = getSessionFlexible(walletId, uidStr, nonce);
    } else {
      session = getSessionFlexible(walletId, currentUserId.toString(), nonce);
    }    
  if (session == null || (!"VERIFIED".equalsIgnoreCase(session.getStatus()) && !"BOUND".equalsIgnoreCase(session.getStatus()))) {
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
    throw new ValidationFailedException("Invite not verified for this account");
  }

    var opt = memberRepo.findByWalletIdAndUserId(walletId, currentUserId);
    WalletMember member;
    if (opt.isEmpty()) {
      member = WalletMember.builder()
          .walletId(walletId)
          .userId(currentUserId)
          .role(WalletMemberRole.valueOf(session.getRole()))
          .status(WalletMemberStatus.ACTIVE)
          .joinedAt(OffsetDateTime.now())
          .updatedAt(OffsetDateTime.now())
          .build();
    } else {
      member = opt.get();
      if (member.getStatus() == WalletMemberStatus.INVITED) {
        member.setStatus(WalletMemberStatus.ACTIVE);
      }
      if (member.getJoinedAt() == null) {
        member.setJoinedAt(OffsetDateTime.now());
      }
      member.setUpdatedAt(OffsetDateTime.now());
    }

    member = memberRepo.save(member);
    upsertRead(member);              
    upsertUserWalletRead(member);
    recountMembersActive(member);
    complete(walletId, currentUserId, nonce, session.getPhone());
    appEvents.publishEvent(WalletInviteAccepted.builder()
            .walletId(walletId)
            .userId(currentUserId)
            .role(WalletMemberRole.valueOf(session.getRole()))
            .occurredAt(OffsetDateTime.now())
            .build()
    );
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

  private void complete(UUID wid, UUID uid, String nonce, String phone) {
    redis.delete(key(wid, uid, nonce));
    redis.delete(String.format(KEY_FMT, wid, "-", nonce));
    if (phone != null) {
      redis.delete(String.format(INDEX_KEY_FMT, wid, phone));
    }
  }
  private void complete(UUID wid, UUID uid, String nonce) {
    complete(wid, uid, nonce, null);
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
  private void recountMembersActive(WalletMember m) {
    long active = memberReadRepo.countByWalletIdAndStatusIn(
        m.getWalletId(),
        java.util.List.of(WalletMemberStatus.ACTIVE)
    );
    walletReadRepo.findById(m.getWalletId()).ifPresent(wr -> {
      wr.setMembersActive((int) active);
      wr.setUpdatedAt(OffsetDateTime.now());
      walletReadRepo.save(wr);
    });
  }
  private void upsertUserWalletRead(WalletMember m) {
    var wrOpt = walletReadRepo.findById(m.getWalletId());

    WalletType wt;
    WalletStatus ws;
    String name;
    if (wrOpt.isPresent()) {
      var wr = wrOpt.get();
      wt = wr.getType();      
      ws = wr.getStatus();    
      name = wr.getName();
    } else {
      var w = walletRepo.findById(m.getWalletId())
          .orElseThrow(() -> new IllegalStateException("Wallet missing in both read & oltp"));
      wt = w.getType();       
      ws = w.getStatus();     
      name = w.getName();
    }

    var uw = UserWalletRead.builder()
        .userId(m.getUserId())
        .walletId(m.getWalletId())
        .isOwner(m.getRole() == WalletMemberRole.OWNER)
        .walletType(wt)        
        .walletStatus(ws)      
        .walletName(name)
        .updatedAt(OffsetDateTime.now())
        .build();

    userWalletReadRepo.save(uw);
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
        .limitCurrency("IDR")
        .dailyLimitRp(m.getDailyLimitRp())     
        .monthlyLimitRp(m.getMonthlyLimitRp()) 
        .perTxLimitRp(m.getPerTxLimitRp())     
        .weeklyLimitRp(m.getWeeklyLimitRp())   
        .joinedAt(m.getJoinedAt())
        .updatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt() : OffsetDateTime.now())
        .build();
    memberReadRepo.save(read);
  }

}
