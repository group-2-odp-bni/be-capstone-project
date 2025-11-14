package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.enums.WalletType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByUserIdAndNameAndType(UUID userId, String name, WalletType type);
}
