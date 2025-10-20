package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.WalletMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletMemberRepository extends JpaRepository<WalletMember, UUID> {
  Optional<WalletMember> findByWalletIdAndUserId(UUID walletId, UUID userId);
}
