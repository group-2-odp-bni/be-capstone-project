package com.bni.orange.wallet.model.request.invite;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class InviteSession {
  private UUID walletId;
  private UUID userId;      // BISA null di flow phone-only
  private String phone;     // E.164 (+62...)
  private String role;      // enum name
  private String codeHash;  // HMAC(code)
  private String nonce;
  private int attempts;
  private int maxAttempts;
  private String status;    // "INVITED"
  private OffsetDateTime createdAt;
}
