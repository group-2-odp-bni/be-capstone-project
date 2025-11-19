package com.bni.orange.wallet.model.request.member;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletMemberInviteRequest {
  private UUID userId;
  private String phone;
  private String email;

  @NotNull
  private WalletMemberRole role;

  private String alias;
  @Valid
  private MemberLimitsUpsertRequest limits;
  private Map<String, Object> metadata;
}
