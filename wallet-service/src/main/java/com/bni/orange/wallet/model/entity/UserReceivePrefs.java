package com.bni.orange.wallet.model.entity;

import lombok.*;
import jakarta.persistence.*;
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
