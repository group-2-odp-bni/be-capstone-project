package com.bni.orange.wallet.model.request.receive;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetDefaultReceiveRequest {
  @NotNull
  private UUID walletId;
}
