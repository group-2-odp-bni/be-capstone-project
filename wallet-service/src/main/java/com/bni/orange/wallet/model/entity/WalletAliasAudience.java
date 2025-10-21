package com.bni.orange.wallet.model.entity;

import lombok.*;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "wallet_oltp", name = "wallet_alias_audience")
@IdClass(WalletAliasAudience.PK.class)
public class WalletAliasAudience {
  @Id @GeneratedValue @org.hibernate.annotations.UuidGenerator @Column(name="alias_id") private UUID aliasId;
  @Id @Column(name="viewer_user_id") private UUID viewerUserId;

  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
  public static class PK implements Serializable {
    private UUID aliasId; private UUID viewerUserId;
  }
}
