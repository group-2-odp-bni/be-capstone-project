package com.bni.orange.wallet.model.entity.read;

import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "wallet_read", name = "user_wallets")
@IdClass(UserWalletRead.PK.class)
public class UserWalletRead {
  @Id @Column private UUID userId;
  @Id @Column private UUID walletId;

  @Column(nullable=false) private boolean isOwner;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "walletType", nullable = false, columnDefinition = "domain.wallet_type")
  private WalletType walletType;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "walletStatus", nullable = false, columnDefinition = "domain.wallet_status")
  private WalletStatus walletStatus;

  @Column(length=160) private String walletName;
  @org.hibernate.annotations.UpdateTimestamp @Column(nullable=false) private OffsetDateTime updatedAt;

  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
  public static class PK implements java.io.Serializable {
    private UUID userId; private UUID walletId;
  }
}
