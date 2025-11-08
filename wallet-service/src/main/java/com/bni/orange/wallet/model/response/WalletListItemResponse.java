package com.bni.orange.wallet.model.response;

import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletListItemResponse {
  private UUID id;
  private String name;
  private WalletType type;
  private WalletStatus status;
  private BigDecimal balanceSnapshot;
  private boolean defaultForUser;
  private OffsetDateTime updatedAt;
}
