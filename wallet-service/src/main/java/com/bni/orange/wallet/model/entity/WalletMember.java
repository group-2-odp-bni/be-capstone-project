package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @DynamicUpdate
@Table(schema = "wallet_oltp", name = "wallet_members",
       uniqueConstraints = @UniqueConstraint(name = "uq_wallet_member", columnNames = {"wallet_id","user_id"}))
public class WalletMember {
  @Id @GeneratedValue @org.hibernate.annotations.UuidGenerator @Column(nullable = false) private UUID id;

  @Column(name="wallet_id", nullable = false) private UUID walletId;
  @Column(name="user_id", nullable = false) private UUID userId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "role", nullable = false, columnDefinition = "domain.wallet_member_role")
  private WalletMemberRole role;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "status", nullable = false, columnDefinition = "domain.wallet_member_status")
  private WalletMemberStatus status = WalletMemberStatus.INVITED;


  @Column private String alias;
  @Column(name="invited_by") private UUID invitedBy;
  @Column(name="invited_at") private OffsetDateTime invitedAt;
  @Column(name="joined_at")  private OffsetDateTime joinedAt;

  @Column(name="per_tx_limit_rp", nullable=false) private long perTxLimitRp;
  @Column(name="weekly_limit_rp", nullable=false) private long weeklyLimitRp;

  @Column(nullable = false) private long dailyLimitRp = 0L;
  @Column(nullable = false) private long monthlyLimitRp = 0L;

  @org.hibernate.annotations.CreationTimestamp @Column(nullable = false) private OffsetDateTime createdAt;
  @org.hibernate.annotations.UpdateTimestamp @Column(nullable = false) private OffsetDateTime updatedAt;
}
