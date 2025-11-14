package com.bni.orange.wallet.repository.read;

import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletMemberReadRepository extends JpaRepository<WalletMemberRead, WalletMemberRead.PK> {
  Optional<WalletMemberRead> findByWalletIdAndUserId(UUID walletId, UUID userId);
  // long countByWalletIdAndStatus(UUID walletId, WalletMemberStatus status);
  long countByWalletIdAndStatusIn(UUID walletId, List<WalletMemberStatus> statuses);

  Page<WalletMemberRead> findByWalletId(UUID walletId, Pageable pageable);
  void deleteByWalletIdAndUserId(java.util.UUID walletId, java.util.UUID userId);
  void deleteAllByWalletId(UUID walletId);

}
