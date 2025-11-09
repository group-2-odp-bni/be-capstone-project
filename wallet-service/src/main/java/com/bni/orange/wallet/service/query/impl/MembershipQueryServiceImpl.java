package com.bni.orange.wallet.service.query.impl;

import com.bni.orange.wallet.exception.business.ForbiddenOperationException;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.response.member.MyRoleResponse;
import com.bni.orange.wallet.model.response.member.MemberLimitsResponse;
import com.bni.orange.wallet.model.response.member.WalletMemberListItemResponse;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.service.query.MembershipQueryService;
import com.bni.orange.wallet.utils.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.bni.orange.wallet.service.invite.InviteSession;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MembershipQueryServiceImpl implements MembershipQueryService {

  private final WalletMemberReadRepository memberReadRepo;
  private final WalletMemberRepository memberRepo;
  private final StringRedisTemplate redis;
  private final ObjectMapper om;

  public MembershipQueryServiceImpl(WalletMemberReadRepository memberReadRepo,
                                    WalletMemberRepository memberRepo,ObjectMapper om,StringRedisTemplate redis) {
    this.memberReadRepo = memberReadRepo;
    this.memberRepo = memberRepo;
        this.om = om;
    this.redis = redis;
  }
  @Override
  public List<WalletMemberListItemResponse> listMembers(UUID walletId, int page, int size, boolean includePending) {
    var me = memberRepo.findByWalletIdAndUserId(walletId, CurrentUser.userId())
        .orElseThrow(() -> new ForbiddenOperationException("You are not a member of this wallet"));

      if (me.getStatus() != WalletMemberStatus.ACTIVE) {
          throw new ForbiddenOperationException("Only ACTIVE members can list wallet members");
      }
      var p = memberReadRepo.findByWalletId(walletId, PageRequest.of(page, size));
      var result = p.getContent().stream()
          .map(this::toListItem)
          .collect(java.util.stream.Collectors.toList());
      if (!includePending) return result;
      var scanPattern = String.format("wallet:invite:%s:-:*", walletId);
      var keys = redis.keys(scanPattern);
      if (keys != null) {
          for (var k : keys) {
              try {
                  var json = redis.opsForValue().get(k);
                  if (json == null) continue;

                  var session = om.readValue(json, InviteSession.class);

                  if (!"INVITED".equalsIgnoreCase(session.getStatus())) continue;

                  var expiresAt = session.getCreatedAt().plusSeconds(300); // TTL = 5 mnt

                  result.add(
                      WalletMemberListItemResponse.builder()
                          .userId(null)
                          .walletId(walletId)
                          .role(WalletMemberRole.valueOf(session.getRole()))
                          .status(WalletMemberStatus.INVITED)
                          .phoneMasked(mask(session.getPhone()))
                          .expiresAt(expiresAt)
                          .build()
                  );

              } catch (Exception ignored) {}
          }
      }

      // 4) Optional sorting: ACTIVE first, then PENDING
      return result.stream()
          .sorted((a, b) -> {
              if (a.getStatus().equals("ACTIVE") && b.getStatus().equals("PENDING")) return -1;
              if (b.getStatus().equals("ACTIVE") && a.getStatus().equals("PENDING")) return 1;
              return 0;
          })
          .toList();
  }
  private static String mask(String e164) {
    if (e164 == null || e164.length() < 8) return e164;
    return e164.substring(0, 6) + "****" + e164.substring(e164.length() - 2);
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
