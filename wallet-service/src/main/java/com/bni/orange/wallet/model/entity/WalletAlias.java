package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.enums.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @DynamicUpdate
@Table(schema = "wallet_oltp", name = "wallet_aliases")
public class WalletAlias {
  @Id private UUID id;

  @Column(nullable = false) private UUID userId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "domain.alias_type")
  private AliasType aliasType;

  @Column(nullable = false, length = 120) private String aliasValue;

  @Column(nullable = false) private UUID walletId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "status", nullable = false, columnDefinition = "domain.route_status")
  private RouteStatus status = RouteStatus.ACTIVE;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "visibility", nullable = false, columnDefinition = "domain.visibility")
  private Visibility visibility = Visibility.PRIVATE;

  @Column(nullable = false, columnDefinition = "jsonb") private String metadata = "{}";
  @org.hibernate.annotations.CreationTimestamp @Column(nullable = false) private OffsetDateTime createdAt;
  @org.hibernate.annotations.UpdateTimestamp @Column(nullable = false) private OffsetDateTime updatedAt;
}
