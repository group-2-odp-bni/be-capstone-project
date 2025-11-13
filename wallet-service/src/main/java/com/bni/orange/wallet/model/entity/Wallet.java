package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map; 
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @DynamicUpdate
@Table(schema = "wallet_oltp", name = "wallets")
public class Wallet {
  @Id @GeneratedValue @org.hibernate.annotations.UuidGenerator @Column(nullable = false) private UUID id;

  @Column(nullable = false) private UUID userId; // creator/legacy owner
  @Column(nullable = false) private String currency = "IDR";


  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "status", nullable = false, columnDefinition = "domain.wallet_status")
  private WalletStatus status = WalletStatus.ACTIVE;

  @Column(nullable = false, precision = 20, scale = 2)
  private BigDecimal balanceSnapshot = BigDecimal.ZERO;


  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "type", nullable = false, columnDefinition = "domain.wallet_type")
  private WalletType type = WalletType.PERSONAL;

  @Column(length = 160) private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata = new HashMap<>();

  @Column(nullable = false) private OffsetDateTime createdAt;
  @Column(nullable = false) private OffsetDateTime updatedAt;

  @PrePersist
  void prePersist() {
    if (status == null) status = WalletStatus.ACTIVE;
    if (balanceSnapshot == null) balanceSnapshot = BigDecimal.ZERO;
    if (currency == null) currency = "IDR";
    if (metadata == null) metadata = new java.util.HashMap<>();
    if (createdAt == null) createdAt = OffsetDateTime.now();
    if (updatedAt == null) updatedAt = createdAt;
  }
}
