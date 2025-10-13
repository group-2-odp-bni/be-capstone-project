package com.bni.orange.transaction.model.entity;


import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
//@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "transfer_oltp", name = "transactions")
@EqualsAndHashCode(callSuper = true)
public class TransferOltpEntity extends BaseEntity{
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "trx_id", nullable = false, unique = true)
    private String trxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxStatus status = TxStatus.PENDING;

    @Column(name = "initiated_by")
    private UUID initiatedBy;
}
