package com.bni.orange.wallet.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
