package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions", schema = "transaction_read")
public class TransactionRead {
    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "trx_id", nullable = false)
    private String trxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TxType type;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TxStatus status;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
