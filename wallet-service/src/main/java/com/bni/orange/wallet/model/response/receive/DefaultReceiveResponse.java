package com.bni.orange.wallet.model.response.receive;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefaultReceiveResponse {
  private UUID walletId;     
  private String walletName; 
}
