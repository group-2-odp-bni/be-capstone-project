package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionRef(String transactionRef);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.senderUserId = :userId OR t.receiverUserId = :userId
            ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE (t.senderUserId = :userId OR t.receiverUserId = :userId)
            AND t.status = :status
            ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByUserIdAndStatus(
        @Param("userId") UUID userId,
        @Param("status") TransactionStatus status,
        Pageable pageable
    );

    @Query("""
            SELECT t FROM Transaction t
            WHERE (t.senderUserId = :userId OR t.receiverUserId = :userId)
            AND t.createdAt BETWEEN :startDate AND :endDate
            ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        Pageable pageable
    );

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.senderUserId = :userId
            ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findBySenderUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.receiverUserId = :userId
            ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findByReceiverUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE (t.senderUserId = :userId OR t.receiverUserId = :userId)
            AND t.status = 'PENDING'
        """)
    long countPendingTransactionsByUserId(@Param("userId") UUID userId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
