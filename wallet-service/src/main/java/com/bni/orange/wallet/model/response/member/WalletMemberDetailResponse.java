package com.bni.orange.wallet.model.response.member;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletMemberDetailResponse {
  private UUID walletId;
  private UUID userId;

  private WalletMemberRole role;
  private WalletMemberStatus status;

  private String alias;
  private UUID invitedBy;         // siapa yang mengundang (optional)
  private OffsetDateTime invitedAt;
  private OffsetDateTime joinedAt;
  private OffsetDateTime updatedAt;

  private MemberLimitsResponse limits;

  private Map<String, Object> metadata; // sudah melalui MetadataFilter saat response
}
