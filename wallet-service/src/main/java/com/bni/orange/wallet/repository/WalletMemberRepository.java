package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.WalletMember;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletMemberRepository extends JpaRepository<WalletMember, UUID> {

  Optional<WalletMember> findByWalletIdAndUserId(UUID walletId, UUID userId);

  boolean existsByWalletIdAndUserId(UUID walletId, UUID userId);

  long countByWalletIdAndRole(UUID walletId, WalletMemberRole role);

  Page<WalletMember> findByWalletId(UUID walletId, Pageable pageable);

  long countByWalletIdAndStatus(UUID walletId, WalletMemberStatus status);
  void deleteByWalletIdAndUserId(java.util.UUID walletId, java.util.UUID userId);
}

