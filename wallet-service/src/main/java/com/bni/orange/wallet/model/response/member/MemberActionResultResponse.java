package com.bni.orange.wallet.model.response.member;

import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemberActionResultResponse {
  private UUID walletId;
  private UUID userId;
  private WalletMemberStatus statusAfter;  // status terbaru setelah action
  private OffsetDateTime occurredAt;       // timestamp action
  private String message;                  // mis. "Invite accepted"
}
