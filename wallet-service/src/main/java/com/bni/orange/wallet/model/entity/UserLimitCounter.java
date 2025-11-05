package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.entity.UserLimitCounterId;
import com.bni.orange.wallet.model.enums.PeriodType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "wallet_oltp", name = "user_limit_counters")
@IdClass(UserLimitCounterId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserLimitCounter {

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "period_type", nullable = false)
  private PeriodType periodType; // DAY/WEEK/MONTH

  @Id
  @Column(name = "period_start", nullable = false)
  private OffsetDateTime periodStart; // start bucket (UTC)

  @Column(name = "amount_used_rp", nullable = false)
  private long amountUsedRp;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  public void prePersist() {
    var now = OffsetDateTime.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
