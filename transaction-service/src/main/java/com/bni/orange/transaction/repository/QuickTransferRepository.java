package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.QuickTransfer;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuickTransferRepository extends JpaRepository<QuickTransfer, UUID> {

    @Query("""
    SELECT qt FROM QuickTransfer qt
    WHERE qt.userId = :userId
      AND qt.recipientPhone LIKE CONCAT('%', :searchTerm, '%')
    """)
    List<QuickTransfer> findByUserIdAndSearchTerm(@Param("userId") UUID userId, @Param("searchTerm") String searchTerm, Sort sort);

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
}
