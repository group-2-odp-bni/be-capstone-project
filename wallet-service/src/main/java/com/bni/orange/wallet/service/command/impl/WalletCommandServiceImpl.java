package com.bni.orange.wallet.service.command.impl;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.model.entity.UserReceivePrefs;
import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.entity.WalletMember;
import com.bni.orange.wallet.model.entity.read.UserWalletRead;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
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
  private final ApplicationEventPublisher appEvents;                // +++
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
    this.appEvents = appEvents;                                        // +++
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
    upsertWalletRead(saved,  true);
    upsertWalletMemberRead(saved.getId(), uid, WalletMemberRole.OWNER, WalletMemberStatus.ACTIVE);
    upsertUserWalletRead(uid, saved);
    boolean wantDefault = Boolean.TRUE.equals(req.getSetAsDefaultReceive());
    boolean hasDefault = prefsRepo.findById(uid).map(UserReceivePrefs::getDefaultWalletId).isPresent();

    if (wantDefault || !hasDefault) {
      markAsDefaultReceive(uid, saved.getId());
    }
    var filtered = MetadataFilter.filter(saved.getMetadata());
    var dto = mapper.mergeDetail(
        walletReadRepo.findById(saved.getId()).orElseThrow(), saved, filtered);
    appEvents.publishEvent(new DomainEvents.WalletCreated(saved.getId(), uid));

    return dto;
    // return mapper.mergeDetail(
    //     walletReadRepo.findById(saved.getId()).orElseThrow(), saved, filtered);
  }

  @Override
  public WalletDetailResponse updateWallet(UUID walletId, WalletUpdateRequest req) {
    final UUID uid = guard.currentUserOrThrow();
    guard.assertCanUpdateWallet(walletId, uid);

    Wallet wl = walletRepo.findById(walletId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

    mapper.patch(wl, req);
    final Wallet saved = walletRepo.save(wl);
    upsertWalletRead(saved,  false);
    upsertUserWalletRead(saved.getUserId(), saved);
    var filtered = MetadataFilter.filter(saved.getMetadata());
    var dto = mapper.mergeDetail(
        walletReadRepo.findById(saved.getId()).orElseThrow(), saved, filtered);
    appEvents.publishEvent(new DomainEvents.WalletUpdated(walletId));             // +++
    return dto;
    // return mapper.mergeDetail(
    //     walletReadRepo.findById(saved.getId()).orElseThrow(), saved, filtered);
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

  private void upsertWalletRead(Wallet src, boolean isCreate) {
    var existing = walletReadRepo.findById(src.getId());
    var read = existing.orElseGet(() -> WalletRead.builder()
        .id(src.getId())
        .isDefaultForUser(false)
        .membersActive(0)
        .build());

    syncMirrorFields(src, read, existing.isEmpty());

    if (read.getMembersActive() == 0) {
        read.setMembersActive(1);
    }

    if (isCreate && read.getMembersActive() == 0) {
      read.setMembersActive(1);
    }

    walletReadRepo.save(read);
  }

  private void upsertWalletMemberRead(UUID walletId, UUID userId,
                                      WalletMemberRole role, WalletMemberStatus status) {
    var r = walletMemberReadRepo.findByWalletIdAndUserId(walletId, userId)
        .orElseGet(() -> WalletMemberRead.builder()
            .walletId(walletId)
            .userId(userId)
            .limitCurrency("IDR")
            .build());
    r.setRole(role);
    r.setStatus(status);
    r.setUpdatedAt(OffsetDateTime.now());
    walletMemberReadRepo.save(r);
  }

  private void upsertUserWalletRead(UUID userId, Wallet src) {
    var idx = userWalletReadRepo.findByUserIdAndWalletId(userId, src.getId())
        .orElseGet(() -> UserWalletRead.builder()
            .userId(userId)
            .walletId(src.getId())
            .build());
    idx.setOwner(true);
    idx.setWalletType(src.getType());
    idx.setWalletStatus(src.getStatus());
    idx.setWalletName(src.getName());
    idx.setUpdatedAt(OffsetDateTime.now());
    userWalletReadRepo.save(idx);
  }

  private void syncMirrorFields(Wallet wl, WalletRead read, boolean isNew) {
    read.setUserId(wl.getUserId());
    read.setCurrency(wl.getCurrency());
    read.setStatus(wl.getStatus());
    read.setBalanceSnapshot(wl.getBalanceSnapshot());
    read.setType(wl.getType());
    read.setName(wl.getName());
    read.setUpdatedAt(wl.getUpdatedAt());
    if (isNew) {
      read.setCreatedAt(wl.getCreatedAt());
    }
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
}
