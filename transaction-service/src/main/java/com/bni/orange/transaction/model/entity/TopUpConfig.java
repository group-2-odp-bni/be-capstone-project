package com.bni.orange.transaction.model.entity;

import com.bni.orange.transaction.model.enums.PaymentProvider;
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
@Table(name = "topup_configs", schema = "transaction_oltp")
public class TopUpConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, unique = true, columnDefinition = "domain.payment_provider")
    private PaymentProvider provider;

    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "min_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "fee_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "fee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal feePercentage;

    @Column(name = "va_expiry_hours", nullable = false)
    private Integer vaExpiryHours;

    @Column(name = "va_prefix", nullable = false, length = 5)
    private String vaPrefix;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_config", columnDefinition = "jsonb")
    private Map<String, Object> providerConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (feeAmount == null) {
            feeAmount = BigDecimal.ZERO;
        }
        if (feePercentage == null) {
            feePercentage = BigDecimal.ZERO;
        }
        if (vaExpiryHours == null) {
            vaExpiryHours = 24;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public BigDecimal calculateFee(BigDecimal amount) {
        BigDecimal percentageFee = amount.multiply(feePercentage).divide(BigDecimal.valueOf(100));
        return feeAmount.add(percentageFee);
    }

    public boolean isAmountValid(BigDecimal amount) {
        return amount.compareTo(minAmount) >= 0 && amount.compareTo(maxAmount) <= 0;
    }
}
