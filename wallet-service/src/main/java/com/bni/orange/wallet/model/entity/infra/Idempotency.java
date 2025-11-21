package com.bni.orange.wallet.model.entity.infra;

import com.bni.orange.wallet.model.enums.IdemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "infra", name = "idempotency",
       uniqueConstraints = @UniqueConstraint(name="uk_scope_key", columnNames = {"scope","idem_key"}))
public class Idempotency {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable=false, length=64)  private String scope;
  @Column(name="idem_key", nullable=false, length=128) private String idemKey;
  @Column(nullable=false, length=64)  private String requestHash;

  private Integer responseStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_body", columnDefinition = "jsonb")
  private String responseBody;                 


  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "status", nullable = false, columnDefinition = "infra.idem_status")
  private IdemStatus status = IdemStatus.PROCESSING;

  @Column(nullable=false) private OffsetDateTime createdAt;
  private OffsetDateTime completedAt;
  @Column(nullable=false) private OffsetDateTime expiresAt;
}
