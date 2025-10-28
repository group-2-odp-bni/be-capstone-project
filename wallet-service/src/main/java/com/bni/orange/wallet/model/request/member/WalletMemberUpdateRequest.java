package com.bni.orange.wallet.model.request.member;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import jakarta.validation.Valid;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletMemberUpdateRequest {

  private WalletMemberRole role;
  private WalletMemberStatus status;
  private String alias;

  @Valid
  private MemberLimitsUpsertRequest limits;
}
