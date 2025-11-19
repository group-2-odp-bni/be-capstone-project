package com.bni.orange.wallet.model.response.member;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MyRoleResponse {
  private UUID walletId;
  private UUID userId;
  private WalletMemberRole role;     // role-ku pada wallet ini
  private WalletMemberStatus status; // ACTIVE/PENDING/SUSPENDED/etc.
}
