package com.bni.orange.wallet.model.entity;

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
@Table(schema = "wallet_oltp", name = "wallet_user_limits")
public class WalletUserLimits {
  @Id @Column(name="user_id") private UUID userId;
  @Column(nullable = false) private int walletCountLimit = 5;
  @Column(nullable = false) private int createdWalletLimit = 10;
  @Column(nullable = false) private int sharedMemberLimit = 5;
  @org.hibernate.annotations.UpdateTimestamp @Column(nullable = false) private OffsetDateTime updatedAt;
}
