package com.bni.orange.wallet.model.entity.read;

import com.bni.orange.wallet.model.enums.*;
import lombok.*;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "wallet_read", name = "alias_directory")
@IdClass(AliasDirectoryRead.PK.class)
public class AliasDirectoryRead {
  @Id
  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "aliasType", nullable = false, columnDefinition = "domain.alias_type")
  private AliasType aliasType;

  @Id @Column(length=120) private String aliasValue;

  @Column(nullable=false) private java.util.UUID userId;
  @Column(nullable=false) private java.util.UUID walletId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false, columnDefinition = "domain.route_status")
  private RouteStatus status;

  @org.hibernate.annotations.UpdateTimestamp @Column(nullable=false) private OffsetDateTime updatedAt;

  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
  public static class PK implements java.io.Serializable {
    private AliasType aliasType; private String aliasValue;
  }
}
