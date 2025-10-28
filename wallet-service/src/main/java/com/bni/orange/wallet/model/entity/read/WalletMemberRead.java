package com.bni.orange.wallet.model.entity.read;

import com.bni.orange.wallet.model.enums.*;
import lombok.*;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

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
