package com.bni.orange.transaction.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "quick_transfers",
    schema = "transaction_oltp",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "recipient_user_id"})
    }
)
public class QuickTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID recipientUserId;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false, length = 50)
    private String recipientPhone;

    @Column(length = 1)
    private String recipientAvatarInitial;

    private OffsetDateTime lastUsedAt;

    @Column(nullable = false)
    private Integer usageCount;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public static String getAvatarInitial(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (usageCount == null) {
            usageCount = 0;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void incrementUsage() {
        this.usageCount++;
        this.lastUsedAt = OffsetDateTime.now();
    }
}
