package com.bni.orange.wallet.model.entity.read;

import com.bni.orange.wallet.model.enums.AliasType;
import com.bni.orange.wallet.model.enums.RouteStatus;
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
