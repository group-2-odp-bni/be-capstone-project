package com.bni.orange.wallet.service.command.impl;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.model.entity.UserReceivePrefs;
import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.entity.WalletMember;
import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.mapper.WalletMapper;
import com.bni.orange.wallet.model.request.wallet.WalletCreateRequest;
import com.bni.orange.wallet.model.request.wallet.WalletUpdateRequest;
import com.bni.orange.wallet.model.response.WalletDetailResponse;
import com.bni.orange.wallet.repository.UserReceivePrefsRepository;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.WalletRepository;
import com.bni.orange.wallet.repository.read.UserWalletReadRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.security.PermissionGuard;
import com.bni.orange.wallet.service.command.WalletCommandService;
import com.bni.orange.wallet.service.infra.IdempotencyService;
import com.bni.orange.wallet.utils.metadata.MetadataFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
      UserReceivePrefsRepository prefsRepo
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
