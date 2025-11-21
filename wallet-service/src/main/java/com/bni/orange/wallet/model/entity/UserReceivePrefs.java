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
@Table(schema = "wallet_oltp", name = "user_receive_prefs")
public class UserReceivePrefs {
  @Id @Column(name="user_id") private UUID userId;
  @Column(name="default_wallet_id", nullable = false) private UUID defaultWalletId;
  @Column(nullable = false) private OffsetDateTime updatedAt;
}
