// messaging/contract/WalletEvent.java
package com.bni.orange.wallet.messaging.contract;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletEvent {

  public enum Type { WalletCreated, WalletUpdated }

  private UUID eventId;
  private Type eventType;
  private OffsetDateTime occurredAt;
  private UUID aggregateId;   // walletId
  private int version;

  private String name;
  private String currency;
  private String type;
  private String status;
  private UUID ownerUserId;

  public static WalletEvent ofCreated(UUID walletId, UUID ownerUserId,
                                      String name, String currency,
                                      String type, String status) {
    return WalletEvent.builder()
        .eventId(UUID.randomUUID())
        .eventType(Type.WalletCreated)
        .occurredAt(OffsetDateTime.now())
        .aggregateId(walletId)
        .version(1)
        .ownerUserId(ownerUserId)
        .name(name)
        .currency(currency)
        .type(type)
        .status(status)
        .build();
  }

  public static WalletEvent ofUpdated(UUID walletId, String name, String status) {
    return WalletEvent.builder()
        .eventId(UUID.randomUUID())
        .eventType(Type.WalletUpdated)
        .occurredAt(OffsetDateTime.now())
        .aggregateId(walletId)
        .version(1)
        .name(name)
        .status(status)
        .build();
  }
}
