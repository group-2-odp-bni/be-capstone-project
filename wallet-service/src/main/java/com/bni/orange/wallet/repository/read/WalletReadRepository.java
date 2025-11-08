package com.bni.orange.wallet.repository.read;

import com.bni.orange.wallet.model.entity.read.WalletRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface WalletReadRepository extends JpaRepository<WalletRead, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO wallet_read.wallets
        (id, user_id, currency, status, balance_snapshot, type, name,
        members_active, is_default_for_user, created_at, updated_at)
        SELECT
        w.id,
        w.user_id,
        COALESCE(w.currency, 'IDR')       AS currency,
        COALESCE(w.status, 'ACTIVE')      AS status,
        :balance                           AS balance_snapshot,   -- pakai nilai 'after'
        COALESCE(w.type, 'PERSONAL')      AS type,
        w.name,
        0                                  AS members_active,
        false                              AS is_default_for_user,
        w.created_at,
        NOW()                              AS updated_at
        FROM wallet_oltp.wallets w
        WHERE w.id = :walletId
        ON CONFLICT (id)
        DO UPDATE SET
        balance_snapshot = EXCLUDED.balance_snapshot,
        updated_at       = NOW()
        """, nativeQuery = true)
    void upsertBalanceSnapshot(@Param("walletId") UUID walletId,
                            @Param("balance") BigDecimal balance);
}
