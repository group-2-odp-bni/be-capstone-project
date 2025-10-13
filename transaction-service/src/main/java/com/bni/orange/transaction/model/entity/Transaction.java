package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions", schema = "transaction_oltp")
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "trx_id", nullable = false, unique = true)
    private String trxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TxType type;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency = "IDR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TxStatus status;
}
