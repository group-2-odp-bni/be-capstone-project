package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.TransactionLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TransactionLedgerRepository extends JpaRepository<TransactionLedger, Long> {

    List<TransactionLedger> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    List<TransactionLedger> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    List<TransactionLedger> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT tl FROM TransactionLedger tl
            WHERE tl.transactionId = :transactionId
            AND tl.walletId = :walletId
        """)
    List<TransactionLedger> findByTransactionIdAndWalletId(
        @Param("transactionId") UUID transactionId,
        @Param("walletId") UUID walletId
    );
}
