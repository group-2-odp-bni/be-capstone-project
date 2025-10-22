package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.enums.WalletType;
import lombok.*;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "wallet_oltp", name = "wallet_type_policy")
public class WalletTypePolicy {
  @Id
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "type", nullable = false, columnDefinition = "domain.wallet_type")
  private WalletType type = WalletType.PERSONAL;

  @Column(nullable = false) private int maxMembers;
  @Column(nullable = false) private long defaultDailyCap;
  @Column(nullable = false) private long defaultMonthlyCap;
  @Column(nullable = false) private boolean allowExternalCredit = true;

  @Column(nullable = false, columnDefinition = "jsonb")
  private String allowMemberDebitRoles; // ["OWNER","ADMIN","SPENDER"]

  @org.hibernate.annotations.UpdateTimestamp @Column(nullable = false) private OffsetDateTime updatedAt;
}
