package com.bni.orange.wallet.model.response.member;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletMemberListItemResponse {
  private UUID walletId;
  private UUID userId;

  private WalletMemberRole role;
  private WalletMemberStatus status;

  private String alias;      
  private OffsetDateTime joinedAt;
  private OffsetDateTime updatedAt;

  private MemberLimitsResponse limits;
}
