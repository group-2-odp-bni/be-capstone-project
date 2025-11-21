package com.bni.orange.wallet.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "wallet_oltp", name = "user_limits")
public class UserLimits {
  @Id @Column(name="user_id") private UUID userId;

  @Column(name="per_tx_max_rp",  nullable=false) private long perTxMaxRp;
  @Column(name="daily_max_rp",   nullable=false) private long dailyMaxRp;
  @Column(name="weekly_max_rp",  nullable=false) private long weeklyMaxRp;
  @Column(name="monthly_max_rp", nullable=false) private long monthlyMaxRp;
  @Column(name="per_tx_min_rp",  nullable=false) private long perTxMinRp;

  @Column(name="enforce_per_tx", nullable=false) private boolean enforcePerTx = true;
  @Column(name="enforce_daily",  nullable=false) private boolean enforceDaily = true;
  @Column(name="enforce_weekly", nullable=false) private boolean enforceWeekly = true;
  @Column(name="enforce_monthly",nullable=false) private boolean enforceMonthly = true;

  private OffsetDateTime effectiveFrom;
  private OffsetDateTime effectiveThrough;

  @Column(nullable=false) private String timezone = "Asia/Jakarta";

  @Column(nullable=false) private OffsetDateTime updatedAt;
  @Column(nullable=false) private OffsetDateTime createdAt;

  @PrePersist
  void prePersist() {
    var now = OffsetDateTime.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
    if (timezone == null) timezone = "Asia/Jakarta";
  }

  @PreUpdate
  void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
