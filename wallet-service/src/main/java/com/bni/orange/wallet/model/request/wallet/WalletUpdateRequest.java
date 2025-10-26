package com.bni.orange.wallet.model.request.wallet;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletUpdateRequest {
  @Size(max=160) private String name; // optional
  private String metadata;            // optional JSON string
}
