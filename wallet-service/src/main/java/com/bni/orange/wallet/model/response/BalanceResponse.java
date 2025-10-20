// model/response/BalanceResponse.java
package com.bni.orange.wallet.model.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BalanceResponse {
  private UUID walletId;
  private BigDecimal balance;
  private String currency;
}
