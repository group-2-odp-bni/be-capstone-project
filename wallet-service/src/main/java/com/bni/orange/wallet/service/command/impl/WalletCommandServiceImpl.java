package com.bni.orange.wallet.service.command.impl;

import com.bni.orange.wallet.client.InternalUserClient;
import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.exception.business.ValidationFailedException;
import com.bni.orange.wallet.model.entity.UserReceivePrefs;
import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.entity.WalletMember;
import com.bni.orange.wallet.model.entity.read.UserWalletRead;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import com.bni.orange.wallet.model.mapper.WalletMapper;
import com.bni.orange.wallet.model.request.wallet.WalletCreateRequest;
import com.bni.orange.wallet.model.request.wallet.WalletUpdateRequest;
import com.bni.orange.wallet.model.response.WalletDetailResponse;
import com.bni.orange.wallet.model.response.wallet.WalletDeleteResultResponse;
import com.bni.orange.wallet.model.response.wallet.WalletDeleteSession;
import com.bni.orange.wallet.repository.UserReceivePrefsRepository;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.WalletRepository;
import com.bni.orange.wallet.repository.read.UserWalletReadRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.security.PermissionGuard;
import com.bni.orange.wallet.service.command.WalletCommandService;
import com.bni.orange.wallet.service.infra.IdempotencyService;
import com.bni.orange.wallet.service.infra.MailService;
import com.bni.orange.wallet.utils.metadata.MetadataFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;          // <-- INI YANG BENAR
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class WalletCommandServiceImpl implements WalletCommandService {

  private final WalletRepository walletRepo;
  private final WalletMemberRepository walletMemberRepo;

  private final WalletReadRepository walletReadRepo;
  private final WalletMemberReadRepository walletMemberReadRepo;
  private final UserWalletReadRepository userWalletReadRepo;

  private final WalletMapper mapper;
  private final ObjectMapper om;
  private final PermissionGuard guard;
  private final IdempotencyService idem;
  private final ApplicationEventPublisher appEvents;               
  private final UserReceivePrefsRepository prefsRepo;

  private final StringRedisTemplate redis;
  private final InternalUserClient internalUserClient;
  private final MailService mailService;

  @Value("${app.wallet-delete.secret}")
  private String walletDeleteSecret;

  @Value("${app.wallet-delete.ttl-seconds:900}")
  private long deleteTtlSeconds;

  private static final String WALLET_DELETE_KEY_FMT = "wallet:delete:%s:%s";
  public WalletCommandServiceImpl(
      WalletRepository walletRepo,
      WalletMemberRepository walletMemberRepo,
      WalletReadRepository walletReadRepo,
      WalletMemberReadRepository walletMemberReadRepo,
      UserWalletReadRepository userWalletReadRepo,
      WalletMapper mapper,
      ObjectMapper om,
      PermissionGuard guard,
      IdempotencyService idem,
      ApplicationEventPublisher appEvents,
      UserReceivePrefsRepository prefsRepo,
      StringRedisTemplate redis,
      InternalUserClient internalUserClient,
      MailService mailService
  ) {
    this.walletRepo = walletRepo;
    this.walletMemberRepo = walletMemberRepo;
    this.walletReadRepo = walletReadRepo;
    this.walletMemberReadRepo = walletMemberReadRepo;
    this.userWalletReadRepo = userWalletReadRepo;
    this.mapper = mapper;
    this.om = om;
    this.guard = guard;
    this.idem = idem;
    this.appEvents = appEvents;                                        
    this.prefsRepo =prefsRepo;
    this.redis = redis;
    this.internalUserClient = internalUserClient;
    this.mailService = mailService;
  }

  @Override
  public WalletDetailResponse createWallet(WalletCreateRequest req, String idempotencyKey) {
    final String scope = "wallet:create";
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      final var payload = canonicalJson(req);
      final var hash = com.bni.orange.wallet.utils.crypto.HashUtil.sha256Hex(payload);
      var replay = idem.begin(scope, idempotencyKey, hash);
      if (replay.isPresent()) {
        try { return om.readValue(replay.get(), WalletDetailResponse.class); }
        catch (Exception ignored) {}
      }
      try {
        var dto = doCreate(req);
        idem.complete(scope, idempotencyKey, HttpStatus.CREATED.value(), toJson(dto));
        return dto;
      } catch (RuntimeException ex) {
        idem.fail(scope, idempotencyKey);
        throw ex;
      }
    }
    return doCreate(req);
  }
  private WalletDetailResponse doCreate(WalletCreateRequest req) {
    final UUID uid = guard.currentUserOrThrow();
    Wallet w = mapper.toEntity(req, uid);
    w.setId(null);
    if (w.getUserId() == null) w.setUserId(uid);
    if (w.getName() == null && w.getType() != null) {
        w.setName(w.getType().name().toLowerCase() + " wallet");
    }
    final Wallet saved = walletRepo.save(w);
    upsertOwnerMembership(saved.getId(), uid);
    boolean wantDefault = Boolean.TRUE.equals(req.getSetAsDefaultReceive());
    boolean hasDefault = prefsRepo.findById(uid).map(UserReceivePrefs::getDefaultWalletId).isPresent();
    boolean isNowDefault = wantDefault || !hasDefault;

    if (isNowDefault) {
        markAsDefaultReceivePrefsOnly(uid, saved.getId());
    }
    appEvents.publishEvent(DomainEvents.WalletCreated.builder()
                    .walletId(saved.getId())
                    .userId(uid)
                    .type(saved.getType())
                    .status(saved.getStatus())
                    .currency(saved.getCurrency())
                    .name(saved.getName())
                    .balanceSnapshot(saved.getBalanceSnapshot())
                    .defaultForUser(isNowDefault)
                    .createdAt(saved.getCreatedAt())
                    .updatedAt(saved.getUpdatedAt())
                    .build());
      var filtered = MetadataFilter.filter(saved.getMetadata());
      var dto = mapper.toDetailResponseFromWalletEntity(saved, filtered,isNowDefault);
      return dto;
  }
  @Override
  public WalletDetailResponse updateWallet(UUID walletId, WalletUpdateRequest req) {
      final UUID uid = guard.currentUserOrThrow();
      guard.assertCanUpdateWallet(walletId, uid);

      Wallet wl = walletRepo.findById(walletId)
              .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

      mapper.patch(wl, req);
      
      final Wallet saved = walletRepo.save(wl);
      appEvents.publishEvent(DomainEvents.WalletUpdated.builder()
              .walletId(saved.getId())
              .userId(saved.getUserId())
              .type(saved.getType())
              .status(saved.getStatus())
              .currency(saved.getCurrency())
              .name(saved.getName())
              .balanceSnapshot(saved.getBalanceSnapshot())
              .updatedAt(saved.getUpdatedAt())
              .build());
      boolean isDefault = prefsRepo.findById(saved.getUserId())
                  .map(UserReceivePrefs::getDefaultWalletId) 
                  .map(defaultId -> defaultId.equals(saved.getId())) 
                  .orElse(false); 
      var filtered = MetadataFilter.filter(saved.getMetadata());
      var dto = mapper.toDetailResponseFromWalletEntity(saved, filtered, isDefault);
      return dto;
    }
  private WalletDeleteResultResponse doDeleteWallet(UUID walletId, UUID actorId) {
      Wallet wallet = walletRepo.findById(walletId)
          .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

      WalletMember member = walletMemberRepo.findByWalletIdAndUserId(walletId, actorId)
          .orElseThrow(() -> new AccessDeniedException("You are not a member of this wallet"));

      WalletType type = wallet.getType();

      if (type == WalletType.PERSONAL) {
          if (!actorId.equals(wallet.getUserId())) {
              throw new AccessDeniedException("Only wallet owner can delete a personal wallet");
          }
          if (member.getRole() != WalletMemberRole.OWNER) {
              throw new AccessDeniedException("Only OWNER can delete a personal wallet");
          }
      } else if (type == WalletType.SHARED) {
          if (member.getRole() != WalletMemberRole.OWNER) {
              throw new AccessDeniedException("Only OWNER can delete a shared wallet");
          }
      } else {
          throw new ValidationFailedException("Wallet type not allowed for deletion");
      }

      UUID destWalletId = resolveDestinationWalletForUser(actorId, walletId);
      Wallet destWallet = walletRepo.findById(destWalletId)
          .orElseThrow(() -> new IllegalStateException("Destination wallet not found"));
      if (destWallet.getStatus() != WalletStatus.ACTIVE) {
          throw new ValidationFailedException("Destination wallet is not ACTIVE");
      }

      BigDecimal balance = wallet.getBalanceSnapshot() != null
          ? wallet.getBalanceSnapshot()
          : BigDecimal.ZERO;
      if (balance.compareTo(BigDecimal.ZERO) > 0) {
          BigDecimal destBalance = destWallet.getBalanceSnapshot() != null
              ? destWallet.getBalanceSnapshot()
              : BigDecimal.ZERO;

          destWallet.setBalanceSnapshot(destBalance.add(balance));
          wallet.setBalanceSnapshot(BigDecimal.ZERO);
      }

      wallet.setStatus(WalletStatus.CLOSED);
      wallet.setUpdatedAt(OffsetDateTime.now());

      walletRepo.save(destWallet);
      walletRepo.save(wallet);

      walletReadRepo.findById(walletId).ifPresent(wr -> {
          wr.setStatus(wallet.getStatus());
          wr.setMembersActive(0);
          wr.setUpdatedAt(wallet.getUpdatedAt());
          walletReadRepo.save(wr);
      });

      List<WalletMember> members = walletMemberRepo
          .findByWalletId(walletId, Pageable.unpaged())
          .getContent();

      for (WalletMember wm : members) {
          UUID memberUserId = wm.getUserId();

          walletMemberRepo.delete(wm);
          walletMemberReadRepo.deleteByWalletIdAndUserId(walletId, memberUserId);
          userWalletReadRepo.findByUserIdAndWalletId(memberUserId, walletId)
              .ifPresent(userWalletReadRepo::delete);
      }

      prefsRepo.findById(actorId).ifPresent(p -> {
          if (walletId.equals(p.getDefaultWalletId())) {
              p.setDefaultWalletId(destWalletId);
              p.setUpdatedAt(OffsetDateTime.now());
              prefsRepo.save(p);
          }
      });

      return WalletDeleteResultResponse.builder()
          .walletId(walletId)
          .destinationWalletId(destWalletId)
          .balanceMoved(balance)
          .message("Wallet deleted and remaining balance moved to default receive wallet")
          .build();
  }
  @Override
  public WalletDeleteResultResponse deleteWallet(UUID walletId) {
      final UUID uid = guard.currentUserOrThrow();

      Wallet wallet = walletRepo.findById(walletId)
          .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

      if (wallet.getType() == WalletType.PERSONAL) {
          return doDeleteWallet(walletId, uid);
      }

      WalletMember member = walletMemberRepo.findByWalletIdAndUserId(walletId, uid)
          .orElseThrow(() -> new AccessDeniedException("You are not a member of this wallet"));

      if (member.getRole() != WalletMemberRole.OWNER) {
          throw new AccessDeniedException("Only OWNER can request deletion for shared wallet");
      }

      var profile = internalUserClient.getUserProfile(uid);
      if (profile.getEmail() == null || Boolean.FALSE.equals(profile.getEmailVerified())) {
          throw new ValidationFailedException("Email not available or not verified");
      }

      String nonce = UUID.randomUUID().toString().replace("-", "");
      var session = WalletDeleteSession.builder()
          .walletId(walletId)
          .ownerId(uid)
          .nonce(nonce)
          .createdAt(OffsetDateTime.now())
          .build();

      String key = String.format(WALLET_DELETE_KEY_FMT, walletId, nonce);
      try {
          String json = om.writeValueAsString(session);
          redis.opsForValue().set(key, json, Duration.ofSeconds(deleteTtlSeconds));
      } catch (Exception e) {
          throw new RuntimeException("Failed to create delete wallet session", e);
      }

      String token = signDeleteToken(walletId, uid, nonce);

      mailService.sendWalletDeleteConfirmationEmail(
          profile.getEmail(),
          wallet.getName(),
          token
      );

      return WalletDeleteResultResponse.builder()
          .walletId(walletId)
          .destinationWalletId(null)
          .balanceMoved(BigDecimal.ZERO)
          .message("Deletion requested. Confirmation email has been sent.")
          .build();
  }
  @Override
  public WalletDeleteResultResponse confirmDeleteWallet(String token) {
      Claims claims = parseDeleteToken(token);
      UUID walletId = UUID.fromString((String) claims.get("wid"));
      UUID ownerId = UUID.fromString((String) claims.get("oid"));
      String nonce = (String) claims.get("n");

      String key = String.format(WALLET_DELETE_KEY_FMT, walletId, nonce);
      String json = redis.opsForValue().get(key);
      if (json == null) {
          throw new ValidationFailedException("Delete session expired or not found");
      }

      WalletDeleteSession session;
      try {
          session = om.readValue(json, WalletDeleteSession.class);
      } catch (Exception e) {
          throw new RuntimeException("Corrupted delete session", e);
      }

      if (!session.getOwnerId().equals(ownerId) || !session.getWalletId().equals(walletId)) {
          throw new ValidationFailedException("Delete session does not match token");
      }

      UUID currentUserId = guard.currentUserOrThrow();
      if (!currentUserId.equals(ownerId)) {
          throw new AccessDeniedException("Delete confirmation is not for this account");
      }

      redis.delete(key);

      return doDeleteWallet(walletId, ownerId);
  }

  private String signDeleteToken(UUID walletId, UUID ownerId, String nonce) {
      long now = System.currentTimeMillis();
      long exp = now + deleteTtlSeconds * 1000L;
      byte[] key = walletDeleteSecret.getBytes(StandardCharsets.UTF_8);

      return Jwts.builder()
          .setSubject("wallet-delete")
          .claim("wid", walletId.toString())
          .claim("oid", ownerId.toString())
          .claim("n", nonce)
          .setIssuedAt(new Date(now))
          .setExpiration(new Date(exp))
          .signWith(Keys.hmacShaKeyFor(key))
          .compact();
  }

  private Claims parseDeleteToken(String token) {
      try {
          return Jwts.parserBuilder()
              .setSigningKey(walletDeleteSecret.getBytes(StandardCharsets.UTF_8))
              .build()
              .parseClaimsJws(token)
              .getBody();
      } catch (JwtException e) {
          throw new ValidationFailedException("Invalid or expired delete token");
      }
  }

private UUID resolveDestinationWalletForUser(UUID userId, UUID sourceWalletId) {
    return prefsRepo.findById(userId)
        .map(UserReceivePrefs::getDefaultWalletId)
        .filter(defId -> !defId.equals(sourceWalletId))
        .orElseThrow(() -> new ValidationFailedException(
            "Cannot delete wallet because no other default receive wallet is configured. " +
            "Please set another wallet as your default receive wallet first."
        ));
}

  private String canonicalJson(Object o) {
    try { return om.writer().withDefaultPrettyPrinter().writeValueAsString(o); }
    catch (Exception e) { throw new RuntimeException(e); }
  }

  private String toJson(Object o) {
    try { return om.writeValueAsString(o); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  @Transactional
  private void markAsDefaultReceivePrefsOnly(UUID userId, UUID newDefaultWalletId) {
      var prefs = prefsRepo.findById(userId)
              .orElse(UserReceivePrefs.builder().userId(userId).build());
    
      prefs.setDefaultWalletId(newDefaultWalletId);
      prefs.setUpdatedAt(OffsetDateTime.now());
      prefsRepo.save(prefs);
  }
  @Transactional
  private void markAsDefaultReceive(UUID userId, UUID newDefaultWalletId) {
    userWalletReadRepo.findByUserIdAndWalletId(userId, newDefaultWalletId)
        .orElseThrow(() -> new AccessDeniedException("You are not a member of this wallet"));

    var prefs = prefsRepo.findById(userId)
        .orElse(UserReceivePrefs.builder().userId(userId).build());
    UUID oldDefault = prefs.getDefaultWalletId();

    prefs.setDefaultWalletId(newDefaultWalletId);
    prefs.setUpdatedAt(OffsetDateTime.now());
    prefsRepo.save(prefs);

    if (oldDefault != null && !oldDefault.equals(newDefaultWalletId)) {
      walletReadRepo.findById(oldDefault).ifPresent(wr -> {
        wr.setDefaultForUser(false);
        walletReadRepo.save(wr);
      });
    }

    WalletRead newWr = walletReadRepo.findById(newDefaultWalletId)
        .orElseThrow(() -> new IllegalStateException("WalletRead data is inconsistent"));
    newWr.setDefaultForUser(true);
    walletReadRepo.save(newWr);
  }
  private void upsertOwnerMembership(UUID walletId, UUID userId) {
    var owner = walletMemberRepo.findByWalletIdAndUserId(walletId, userId)
            .orElseGet(() -> WalletMember.builder()
                    .walletId(walletId)
                    .userId(userId)
                    .role(WalletMemberRole.OWNER)
                    .status(WalletMemberStatus.ACTIVE)
                    .build());
    owner.setRole(WalletMemberRole.OWNER);
    owner.setStatus(WalletMemberStatus.ACTIVE);
    walletMemberRepo.save(owner);
  }
}
