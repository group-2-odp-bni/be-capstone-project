package com.bni.orange.wallet.service.query;

import com.bni.orange.wallet.model.response.member.MyRoleResponse;
import com.bni.orange.wallet.model.response.member.WalletMemberListItemResponse;

import java.util.List;
import java.util.UUID;

public interface MembershipQueryService {
  List<WalletMemberListItemResponse> listMembers(UUID walletId, int page, int size);
  MyRoleResponse getMyRole(UUID walletId);
}
