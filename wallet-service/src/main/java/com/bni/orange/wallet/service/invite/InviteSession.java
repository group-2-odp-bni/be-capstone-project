package com.bni.orange.wallet.service.invite;

import lombok.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteSession implements Serializable {
  private UUID walletId;
  private UUID userId;
  private String phone;
  private String role;       
  private String codeHash;   
  private String nonce;      
  private int attempts;
  private int maxAttempts;
  private String status;     
  private OffsetDateTime createdAt;
}
