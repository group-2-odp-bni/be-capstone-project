package com.bni.orange.wallet.security;

import com.bni.orange.wallet.exception.business.ForbiddenOperationException;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.utils.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.UUID;

@Component
public class PermissionGuard {

  private static final EnumSet<WalletMemberStatus> ACTIVE_STATUSES =
      EnumSet.of(WalletMemberStatus.ACTIVE); // bisa tambah INVITED bila diperlukan

  private static final EnumSet<WalletMemberRole> ADMIN_ROLES =
      EnumSet.of(WalletMemberRole.OWNER, WalletMemberRole.ADMIN);

  private final WalletMemberReadRepository memberReadRepo;
  private final WalletReadRepository walletReadRepo;

  public PermissionGuard(WalletMemberReadRepository memberReadRepo,
                         WalletReadRepository walletReadRepo) {
    this.memberReadRepo = memberReadRepo;
    this.walletReadRepo = walletReadRepo;
  }

  public UUID currentUserOrThrow() {
    return CurrentUser.userId();
  }

  /** Pastikan wallet ada. */
  public void assertWalletExists(UUID walletId) {
    walletReadRepo.findById(walletId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
  }

  /** Harus anggota ACTIVE. */
  public WalletMemberRead assertMemberActive(UUID walletId, UUID userId) {
    var m = memberReadRepo.findByWalletIdAndUserId(walletId, userId)
        .orElseThrow(() -> new ForbiddenOperationException("Not a member of this wallet"));
    if (!ACTIVE_STATUSES.contains(m.getStatus())) {
      throw new ForbiddenOperationException("Membership not active");
    }
    return m;
  }

  /** OWNER/ADMIN ACTIVE saja yang boleh update. */
  public void assertCanUpdateWallet(UUID walletId, UUID userId) {
    assertWalletExists(walletId);
    var m = assertMemberActive(walletId, userId);
    if (!ADMIN_ROLES.contains(m.getRole())) {
      throw new ForbiddenOperationException("Require OWNER or ADMIN to update");
    }
  }
}
