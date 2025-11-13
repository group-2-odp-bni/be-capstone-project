package com.bni.orange.wallet.model.response.invite;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class InviteInspectResponse {
  private String status;           // VALID | EXPIRED
  private UUID walletId;
  private String walletName;       // optional
  private String role;             // OWNER/ADMIN/MEMBER/VIEWER
  private String phoneMasked;      
  private OffsetDateTime expiresAt;
  private boolean requiresAccount; 
}
