package com.bni.orange.wallet.messaging.events;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletUpdatedEvent {
  private UUID eventId;
  private UUID walletId;
  private String name;                
  private OffsetDateTime updatedAt;
}

