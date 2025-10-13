package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.WalletStatusProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WalletStatusProjectionRepository extends JpaRepository<WalletStatusProjection, UUID> {
}
