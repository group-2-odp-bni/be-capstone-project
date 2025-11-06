package com.bni.orange.wallet.service.command.projector;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.model.entity.UserReceivePrefs;
import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.entity.read.UserWalletRead;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.repository.UserReceivePrefsRepository;
import com.bni.orange.wallet.repository.read.UserWalletReadRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WalletReadModelProjector {
    private final WalletReadRepository walletReadRepo;
    private final WalletMemberReadRepository walletMemberReadRepo;
    private final UserWalletReadRepository userWalletReadRepo;
    private final UserReceivePrefsRepository prefsRepo;

    public void projectNewWallet(DomainEvents.WalletCreated event) {
        WalletRead wr = WalletRead.builder()
                .id(event.getWalletId())
                .userId(event.getUserId())
                .currency(event.getCurrency())
                .status(event.getStatus())
                .balanceSnapshot(event.getBalanceSnapshot())
                .type(event.getType())
                .name(event.getName())
                .membersActive(1)
                .isDefaultForUser(false) 
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
        walletReadRepo.save(wr);
        upsertWalletMemberRead(event.getWalletId(), event.getUserId(), WalletMemberRole.OWNER, WalletMemberStatus.ACTIVE);
        UserWalletRead idx = UserWalletRead.builder()
                .userId(event.getUserId())
                .walletId(event.getWalletId())
                .isOwner(true)
                .walletType(event.getType())
                .walletStatus(event.getStatus())
                .walletName(event.getName())
                .build();
        userWalletReadRepo.save(idx);
        if (event.isDefaultForUser()) {
            markAsDefaultReceive(event.getUserId(), event.getWalletId());
        }
    }

    @Transactional
    public void projectWalletUpdateFromEvent(DomainEvents.WalletUpdated event) {
        WalletRead wr = walletReadRepo.findById(event.getWalletId())
                .orElseThrow(() -> new IllegalStateException("WalletRead data is inconsistent for " + event.getWalletId()));
        wr.setName(event.getName());
        wr.setStatus(event.getStatus());
        wr.setType(event.getType());
        wr.setCurrency(event.getCurrency());
        wr.setBalanceSnapshot(event.getBalanceSnapshot());
        wr.setUpdatedAt(event.getUpdatedAt());
        walletReadRepo.save(wr);
        UserWalletRead idx = userWalletReadRepo.findByUserIdAndWalletId(event.getUserId(), event.getWalletId())
                .orElseThrow(() -> new IllegalStateException("UserWalletRead data is inconsistent for " + event.getWalletId()));

        idx.setWalletName(event.getName());
        idx.setWalletStatus(event.getStatus());
        idx.setWalletType(event.getType());
        idx.setUpdatedAt(OffsetDateTime.now());
        userWalletReadRepo.save(idx);
    }
    @Transactional
    public void upsertWalletRead(Wallet src, boolean isCreate) {
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

    @Transactional
    public void upsertWalletMemberRead(UUID walletId, UUID userId,
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

    @Transactional
    public void upsertUserWalletRead(UUID userId, Wallet src) {
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

    @Transactional
    public void markAsDefaultReceive(UUID userId, UUID newDefaultWalletId) {
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
}