package com.bni.orange.wallet.model.request.member;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletMemberUpdateRequest {

  private WalletMemberRole role;
  private WalletMemberStatus status;
  private String alias;

  @Valid
  private MemberLimitsUpsertRequest limits;
}
