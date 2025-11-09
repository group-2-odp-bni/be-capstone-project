package com.bni.orange.wallet.model.request.invite;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class InviteSession {
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
