package com.bni.orange.wallet.model.request.wallet;

import com.bni.orange.wallet.model.enums.WalletType;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletCreateRequest {
  @NotNull private WalletType type;       // PERSONAL | SHARED | FAMILY
  @Size(max=160) private String name;     // optional
  private String metadata;                // optional
}
