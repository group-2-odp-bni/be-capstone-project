package com.bni.orange.wallet.messaging.events;

import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletCreatedEvent {
  private UUID eventId;
  private UUID walletId;
  private UUID userId;
  private WalletType type;
  private WalletStatus status;
  private String currency;
  private String name;
  private BigDecimal balanceSnapshot;
  private boolean defaultForUser;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}

