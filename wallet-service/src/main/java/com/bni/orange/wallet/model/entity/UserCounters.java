package com.bni.orange.wallet.model.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "wallet_oltp", name = "user_counters")
public class UserCounters {
  @Id @Column(name="user_id") private UUID userId;
  @Column(nullable = false) private int walletCreatedTotal = 0;
  @Column(nullable = false) private OffsetDateTime updatedAt;
}
