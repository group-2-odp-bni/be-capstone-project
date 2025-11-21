package com.bni.orange.wallet.model.request.wallet;

import com.bni.orange.wallet.model.enums.WalletType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreateRequest {
  @NotNull private WalletType type;       // PERSONAL | SHARED
  @Size(max=160) private String name;
  private Map<String, Object> metadata;
  private Boolean setAsDefaultReceive;
}
