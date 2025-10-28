package com.bni.orange.wallet.repository.read;

import com.bni.orange.wallet.model.entity.read.WalletRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WalletReadRepository extends JpaRepository<WalletRead, UUID> {
}
