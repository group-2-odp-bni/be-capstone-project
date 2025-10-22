package com.bni.orange.transaction.repository;

import com.bni.orange.transaction.model.entity.TopUpConfig;
import com.bni.orange.transaction.model.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopUpConfigRepository extends JpaRepository<TopUpConfig, UUID> {

    Optional<TopUpConfig> findByProvider(PaymentProvider provider);

    @Query("""
            SELECT tc FROM TopUpConfig tc
            WHERE tc.isActive = true
            ORDER BY tc.displayOrder ASC
        """)
    List<TopUpConfig> findAllActiveProviders();

    @Query("""
            SELECT tc FROM TopUpConfig tc
            WHERE tc.provider = :provider
            AND tc.isActive = true
        """)
    Optional<TopUpConfig> findActiveByProvider(@Param("provider") PaymentProvider provider);

    boolean existsByProvider(PaymentProvider provider);
}
