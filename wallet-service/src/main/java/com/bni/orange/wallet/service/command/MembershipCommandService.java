package com.bni.orange.wallet.service.command;

import com.bni.orange.wallet.model.request.member.WalletMemberInviteRequest;
import com.bni.orange.wallet.model.request.member.WalletMemberUpdateRequest;
import com.bni.orange.wallet.model.response.member.MemberActionResultResponse;
import com.bni.orange.wallet.model.response.member.WalletMemberDetailResponse;

import java.util.UUID;

public interface MembershipCommandService {
  WalletMemberDetailResponse inviteMember(UUID walletId, WalletMemberInviteRequest req, String idemKey);
  WalletMemberDetailResponse updateMember(UUID walletId, UUID userId, WalletMemberUpdateRequest req);
  MemberActionResultResponse removeMember(UUID walletId, UUID userId);

  MemberActionResultResponse leaveWallet(UUID walletId);
}
