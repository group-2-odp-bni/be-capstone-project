package com.bni.orange.wallet.model.request.receive;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetDefaultReceiveRequest {
  @NotNull
  private UUID walletId;
}
