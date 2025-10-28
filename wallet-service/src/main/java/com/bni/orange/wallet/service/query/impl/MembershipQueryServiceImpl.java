package com.bni.orange.wallet.service.query.impl;

import com.bni.orange.wallet.exception.business.ForbiddenOperationException;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.response.member.MyRoleResponse;
import com.bni.orange.wallet.model.response.member.MemberLimitsResponse;
import com.bni.orange.wallet.model.response.member.WalletMemberListItemResponse;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.service.query.MembershipQueryService;
import com.bni.orange.wallet.utils.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MembershipQueryServiceImpl implements MembershipQueryService {

  private final WalletMemberReadRepository memberReadRepo;
  private final WalletMemberRepository memberRepo;

  public MembershipQueryServiceImpl(WalletMemberReadRepository memberReadRepo,
                                    WalletMemberRepository memberRepo) {
    this.memberReadRepo = memberReadRepo;
    this.memberRepo = memberRepo;
  }

  @Override
  public List<WalletMemberListItemResponse> listMembers(UUID walletId, int page, int size) {
    var me = memberRepo.findByWalletIdAndUserId(walletId, CurrentUser.userId())
        .orElseThrow(() -> new ForbiddenOperationException("You are not a member of this wallet"));

    if (me.getStatus() != WalletMemberStatus.ACTIVE) {
      throw new ForbiddenOperationException("Only ACTIVE members can list wallet members");
    }

    var p = memberReadRepo.findByWalletId(walletId, PageRequest.of(page, size));
    return p.getContent().stream().map(this::toListItem).toList();
  }

  @Override
  public MyRoleResponse getMyRole(UUID walletId) {
    var uid = CurrentUser.userId();
    var me = memberReadRepo.findByWalletIdAndUserId(walletId, uid)
        .orElseThrow(() -> new ResourceNotFoundException("You are not a member of this wallet"));

    return MyRoleResponse.builder()
        .walletId(walletId)
        .userId(uid)
        .role(me.getRole())
        .status(me.getStatus())
        .build();
  }

  private WalletMemberListItemResponse toListItem(WalletMemberRead r) {
    return WalletMemberListItemResponse.builder()
        .walletId(r.getWalletId())
        .userId(r.getUserId())
        .role(r.getRole())
        .status(r.getStatus())
        .alias(r.getAlias())
        .joinedAt(r.getJoinedAt())
        .updatedAt(r.getUpdatedAt())
        .limits(MemberLimitsResponse.builder()
            .perTransaction(java.math.BigDecimal.valueOf(r.getPerTxLimitRp()))
            .daily(java.math.BigDecimal.valueOf(r.getDailyLimitRp()))
            .weekly(java.math.BigDecimal.valueOf(r.getWeeklyLimitRp()))
            .monthly(java.math.BigDecimal.valueOf(r.getMonthlyLimitRp()))
            .currency(r.getLimitCurrency())
            .build())
        .build();
  }

}
