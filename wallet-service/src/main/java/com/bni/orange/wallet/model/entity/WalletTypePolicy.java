package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.enums.WalletType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;

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
