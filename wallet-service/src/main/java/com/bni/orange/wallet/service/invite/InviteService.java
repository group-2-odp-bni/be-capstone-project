package com.bni.orange.wallet.service.invite;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.request.invite.GeneratedInvite;
import com.bni.orange.wallet.model.response.invite.InviteInspectResponse;
import com.bni.orange.wallet.model.response.invite.VerifyInviteCodeResponse;
import com.bni.orange.wallet.model.response.member.MemberActionResultResponse;

import java.util.UUID;

public interface InviteService {
  GeneratedInvite generateInviteLink(UUID walletId, UUID userId, String phoneE164, WalletMemberRole role);
  InviteInspectResponse inspect(String token);      
  MemberActionResultResponse acceptToken(String token);
  VerifyInviteCodeResponse verifyCode(String token, String code);

}


