package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.QuickTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuickTransferRepository extends JpaRepository<QuickTransfer, UUID> {

    // Legacy methods - kept for backward compatibility (based on userId)
    List<QuickTransfer> findByUserIdOrderByDisplayOrderAsc(UUID userId);

    List<QuickTransfer> findByUserIdOrderByUsageCountDesc(UUID userId);

    List<QuickTransfer> findByUserIdOrderByLastUsedAtDesc(UUID userId);

    Optional<QuickTransfer> findByUserIdAndRecipientUserId(UUID userId, UUID recipientUserId);

    boolean existsByUserIdAndRecipientUserId(UUID userId, UUID recipientUserId);

    long countByUserId(UUID userId);

    @Query("""
            SELECT qt FROM QuickTransfer qt
            WHERE qt.userId = :userId
            ORDER BY qt.usageCount DESC, qt.lastUsedAt DESC
        """)
    List<QuickTransfer> findTopByUserId(@Param("userId") UUID userId);

    void deleteByUserIdAndRecipientUserId(UUID userId, UUID recipientUserId);

    // New wallet-based methods for multi-wallet support
    List<QuickTransfer> findByWalletIdOrderByDisplayOrderAsc(UUID walletId);

    List<QuickTransfer> findByWalletIdOrderByUsageCountDesc(UUID walletId);

    List<QuickTransfer> findByWalletIdOrderByLastUsedAtDesc(UUID walletId);

    Optional<QuickTransfer> findByWalletIdAndRecipientUserId(UUID walletId, UUID recipientUserId);

    boolean existsByWalletIdAndRecipientUserId(UUID walletId, UUID recipientUserId);

    long countByWalletId(UUID walletId);

    @Query("""
            SELECT qt FROM QuickTransfer qt
            WHERE qt.walletId = :walletId
            ORDER BY qt.usageCount DESC, qt.lastUsedAt DESC
        """)
    List<QuickTransfer> findTopByWalletId(@Param("walletId") UUID walletId);

    void deleteByWalletIdAndRecipientUserId(UUID walletId, UUID recipientUserId);
}
