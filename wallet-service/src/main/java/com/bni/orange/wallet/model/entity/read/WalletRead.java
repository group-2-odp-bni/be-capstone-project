package com.bni.orange.wallet.model.entity.read;

import com.bni.orange.wallet.model.enums.WalletStatus;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(schema = "wallet_read", name = "wallets")
public class WalletRead {
  @Id private UUID id;
  @Column(nullable=false) private UUID userId;
  @Column(nullable=false) private String currency = "IDR";

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "status", nullable = false, columnDefinition = "domain.wallet_status")
  private WalletStatus status;

  @Column(nullable=false, precision=20, scale=2) private BigDecimal balanceSnapshot = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)                       
  @Column(name = "type", nullable = false, columnDefinition = "domain.wallet_type")
  private WalletType type;

  @Column(length=160) private String name;
  @Column(nullable=false) private int membersActive = 0;
  @Column(nullable=false) private boolean isDefaultForUser = false;

  @Column(nullable=false) private OffsetDateTime createdAt;
  @Column(nullable=false) private OffsetDateTime updatedAt;
}
