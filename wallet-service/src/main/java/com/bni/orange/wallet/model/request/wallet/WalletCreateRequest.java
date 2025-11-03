package com.bni.orange.wallet.model.request.wallet;

import com.bni.orange.wallet.model.enums.WalletType;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.Map;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletCreateRequest {
  @NotNull private WalletType type;       // PERSONAL | SHARED
  @Size(max=160) private String name;
  private Map<String, Object> metadata;
  private Boolean setAsDefaultReceive;
}
