package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transactions", schema = "transaction_oltp")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 50)
    private String transactionRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "domain.tx_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "domain.tx_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TransactionStatus status;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee", nullable = false, precision = 20, scale = 2)
    private BigDecimal fee;

    @Column(name = "total_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "counterparty_user_id")
    private UUID counterpartyUserId;

    @Column(name = "counterparty_wallet_id")
    private UUID counterpartyWalletId;

    @Column(name = "counterparty_name", length = 255)
    private String counterpartyName;

    @Column(name = "counterparty_phone", length = 50)
    private String counterpartyPhone;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "notes", length = 255)
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
        if (currency == null) {
            currency = "IDR";
        }
        if (fee == null) {
            fee = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void markAsProcessing() {
        if (!status.canBeProcessed()) {
            throw new IllegalStateException("Transaction cannot be processed in current state: " + status);
        }
        this.status = TransactionStatus.PROCESSING;
    }

    public void markAsSuccess() {
        this.status = TransactionStatus.SUCCESS;
        this.completedAt = OffsetDateTime.now();
        this.failedAt = null;
        this.failureReason = null;
    }

    public void markAsFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.failedAt = OffsetDateTime.now();
        this.failureReason = reason;
        this.completedAt = null;
    }

    public boolean belongsToUser(UUID targetUserId) {
        return userId.equals(targetUserId);
    }

    public boolean isTransfer() {
        return type != null && type.isTransfer();
    }

    public boolean isDebit() {
        return type != null && type.isDebit();
    }

    public boolean isCredit() {
        return type != null && type.isCredit();
    }

    public void calculateTotalAmount() {
        this.totalAmount = this.amount.add(this.fee);
    }
}
