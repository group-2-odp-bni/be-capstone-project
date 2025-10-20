// model/response/WalletDetailResponse.java
package com.bni.orange.wallet.model.response;

import com.bni.orange.wallet.model.enums.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletDetailResponse {
  private UUID id;
  private UUID userId;                
  private String currency;
  private WalletStatus status;
  private WalletType type;
  private String name;
  private BigDecimal balanceSnapshot;
  private boolean defaultForUser;     
  private Map<String,Object> metadata;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
