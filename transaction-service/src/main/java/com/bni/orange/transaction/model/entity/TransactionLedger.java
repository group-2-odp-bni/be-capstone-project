package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.LedgerEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;


@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transaction_ledger", schema = "transaction_oltp")
public class TransactionLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "transaction_ref", nullable = false, length = 50)
    private String transactionRef;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "performed_by_user_id")
    private UUID performedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 20, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 20, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static TransactionLedger createDebitEntry(
        UUID transactionId,
        String transactionRef,
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        String description
    ) {
        return TransactionLedger.builder()
            .transactionId(transactionId)
            .transactionRef(transactionRef)
            .walletId(walletId)
            .userId(userId)
            .entryType(LedgerEntryType.DEBIT)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceBefore.subtract(amount))
            .description(description)
            .build();
    }

    public static TransactionLedger createCreditEntry(
        UUID transactionId,
        String transactionRef,
        UUID walletId,
        UUID userId,
        BigDecimal amount,
        BigDecimal balanceBefore,
        String description
    ) {
        return TransactionLedger.builder()
            .transactionId(transactionId)
            .transactionRef(transactionRef)
            .walletId(walletId)
            .userId(userId)
            .entryType(LedgerEntryType.CREDIT)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceBefore.add(amount))
            .description(description)
            .build();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
