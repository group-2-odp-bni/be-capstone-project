package com.bni.orange.wallet.model.request.wallet;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletUpdateRequest {
  @Size(max=160) private String name; // optional
  private Map<String, Object> metadata;            // optional JSON string
}
