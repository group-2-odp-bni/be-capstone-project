package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "transfer_read", name = "transactions")
@EqualsAndHashCode(callSuper = true)
public class TransferReadEntity extends BaseEntity {

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "trx_id", nullable = false, unique = true)
    private String trxId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "tx_type")
    private TxType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "tx_status")
    private TxStatus status = TxStatus.PENDING;

    @Column(name = "initiated_by")
    private UUID initiatedBy;
}
