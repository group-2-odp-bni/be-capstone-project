package com.bni.orange.wallet.messaging.events;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletBalanceUpdatedEvent {
  private UUID eventId;
  private UUID walletId;
  private BigDecimal balanceSnapshot;
  private OffsetDateTime updatedAt;
}
