package com.bni.orange.wallet.model.entity.read;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(schema = "wallet_read", name = "user_limits")
public class UserLimitsRead {
  @Id @Column private UUID userId;

  @Column(nullable=false) private long perTxMaxRp;
  @Column(nullable=false) private long dailyMaxRp;
  @Column(nullable=false) private long weeklyMaxRp;
  @Column(nullable=false) private long monthlyMaxRp;
  @Column(nullable=false) private long perTxMinRp;

  @Column(nullable=false) private boolean enforcePerTx;
  @Column(nullable=false) private boolean enforceDaily;
  @Column(nullable=false) private boolean enforceWeekly;
  @Column(nullable=false) private boolean enforceMonthly;

  private java.time.OffsetDateTime effectiveFrom;
  private java.time.OffsetDateTime effectiveThrough;

  @Column(nullable=false) private String timezone;
  @org.hibernate.annotations.UpdateTimestamp @Column(nullable=false) private OffsetDateTime updatedAt;
}
