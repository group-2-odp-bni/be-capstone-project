package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.TransactionOltpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionOltpRepository extends JpaRepository<TransactionOltpEntity, UUID> {
    Optional<TransactionOltpEntity> findByTrxId(String trxId);

}
