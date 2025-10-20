package com.bni.orange.wallet.repository.read;

import com.bni.orange.wallet.model.entity.read.UserWalletRead;
import com.bni.orange.wallet.model.entity.read.UserWalletRead.PK;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserWalletReadRepository extends JpaRepository<UserWalletRead, PK> {
  Page<UserWalletRead> findByUserId(UUID userId, Pageable pageable);

  Optional<UserWalletRead> findByUserIdAndWalletId(UUID userId, UUID walletId);
}
