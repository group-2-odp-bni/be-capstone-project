package com.bni.orange.wallet.model.entity.read;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
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
@Table(schema = "wallet_read", name = "wallet_members")
@IdClass(WalletMemberRead.PK.class)
public class WalletMemberRead {
  @Id @Column private UUID walletId;
  @Id @Column private UUID userId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "role", nullable = false, columnDefinition = "domain.wallet_member_role")
  private WalletMemberRole role;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false, columnDefinition = "domain.wallet_member_status")
  private WalletMemberStatus status;

  @Column(nullable=false) private long dailyLimitRp;
  @Column(nullable=false) private long monthlyLimitRp;
  @org.hibernate.annotations.UpdateTimestamp @Column(nullable=false) private OffsetDateTime updatedAt;

  @Column private String alias;
  @Column(name = "joined_at") private OffsetDateTime joinedAt;

  @Column(name = "per_tx_limit_rp", nullable=false) private long perTxLimitRp;
  @Column(name = "weekly_limit_rp", nullable=false) private long weeklyLimitRp;
  @Column(name = "limit_currency",   nullable=false) private String limitCurrency = "IDR";

  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
  public static class PK implements java.io.Serializable {
    private UUID walletId; private UUID userId;
  }
}
