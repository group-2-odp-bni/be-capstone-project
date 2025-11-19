package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.PaymentProvider;
import com.bni.orange.transaction.model.enums.VirtualAccountStatus;
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
@Table(name = "virtual_accounts", schema = "transaction_oltp")
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "va_number", nullable = false, unique = true, length = 20)
    private String vaNumber;

    @Column(name = "account_name", length = 255)
    private String accountName;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, columnDefinition = "domain.payment_provider")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "domain.va_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private VirtualAccountStatus status;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", precision = 20, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "callback_received_at")
    private OffsetDateTime callbackReceivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "callback_payload", columnDefinition = "jsonb")
    private Map<String, Object> callbackPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) {
            status = VirtualAccountStatus.ACTIVE;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void markAsPaid(BigDecimal paidAmount, Map<String, Object> callbackPayload) {
        if (!status.canBePaid()) {
            throw new IllegalStateException("VA cannot be paid in current state: " + status);
        }
        this.status = VirtualAccountStatus.PAID;
        this.paidAmount = paidAmount;
        this.paidAt = OffsetDateTime.now();
        this.callbackPayload = callbackPayload;
        this.callbackReceivedAt = OffsetDateTime.now();
    }

    public void markAsExpired() {
        if (status.isFinal()) {
            throw new IllegalStateException("VA is already in final state: " + status);
        }
        this.status = VirtualAccountStatus.EXPIRED;
        this.expiredAt = OffsetDateTime.now();
    }

    public void markAsCancelled() {
        if (!status.canBeCancelled()) {
            throw new IllegalStateException("VA cannot be cancelled in current state: " + status);
        }
        this.status = VirtualAccountStatus.CANCELLED;
        this.cancelledAt = OffsetDateTime.now();
    }

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == VirtualAccountStatus.ACTIVE && !isExpired();
    }
}
