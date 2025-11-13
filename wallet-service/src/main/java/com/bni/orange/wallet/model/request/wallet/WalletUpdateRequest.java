package com.bni.orange.wallet.model.request.wallet;

import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.Map;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletUpdateRequest {
  @Size(max=160) private String name; // optional
  private Map<String, Object> metadata;            // optional JSON string
}
