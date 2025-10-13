package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.WalletStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wallet_status_projection", schema = "transaction_read")
public class WalletStatusProjection {

    @Id
    @Column(name = "wallet_id")
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WalletStatus status;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
