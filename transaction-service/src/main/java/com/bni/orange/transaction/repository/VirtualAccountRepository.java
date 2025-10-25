package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.VirtualAccount;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT va FROM VirtualAccount va WHERE va.vaNumber = :vaNumber")
    Optional<VirtualAccount> findByVaNumberWithLock(@Param("vaNumber") String vaNumber);

    Optional<VirtualAccount> findByVaNumber(String vaNumber);

    Optional<VirtualAccount> findByTransactionId(UUID transactionId);

    @Query("""
            SELECT va FROM VirtualAccount va
            WHERE va.userId = :userId
            ORDER BY va.createdAt DESC
        """)
    Page<VirtualAccount> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT va FROM VirtualAccount va
            WHERE va.userId = :userId
            AND va.status = :status
            ORDER BY va.createdAt DESC
        """)
    Page<VirtualAccount> findByUserIdAndStatus(
        @Param("userId") UUID userId,
        @Param("status") VirtualAccountStatus status,
        Pageable pageable
    );

    @Query("""
            SELECT va FROM VirtualAccount va
            WHERE va.status = :status
            AND va.expiresAt < :currentTime
        """)
    List<VirtualAccount> findExpiredVirtualAccounts(
        @Param("status") VirtualAccountStatus status,
        @Param("currentTime") OffsetDateTime currentTime
    );

    @Query("""
            SELECT va FROM VirtualAccount va
            WHERE va.provider = :provider
            AND va.status = :status
            ORDER BY va.createdAt DESC
        """)
    Page<VirtualAccount> findByProviderAndStatus(
        @Param("provider") PaymentProvider provider,
        @Param("status") VirtualAccountStatus status,
        Pageable pageable
    );

    boolean existsByVaNumber(String vaNumber);

    @Query("""
            SELECT COUNT(va) FROM VirtualAccount va
            WHERE va.userId = :userId
            AND va.status = 'ACTIVE'
        """)
    long countActiveVirtualAccountsByUserId(@Param("userId") UUID userId);

    List<VirtualAccount> findByStatusAndExpiresAtBefore(
        VirtualAccountStatus status,
        OffsetDateTime expiryTime
    );

    long countByStatus(VirtualAccountStatus status);

    long countByStatusAndExpiresAtBetween(
        VirtualAccountStatus status,
        OffsetDateTime startTime,
        OffsetDateTime endTime
    );
}
