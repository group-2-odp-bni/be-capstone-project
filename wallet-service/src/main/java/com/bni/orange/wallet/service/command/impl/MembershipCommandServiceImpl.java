package com.bni.orange.wallet.service.command.impl;

import com.bni.orange.wallet.domain.DomainEvents;
import com.bni.orange.wallet.exception.business.ConflictException;
import com.bni.orange.wallet.exception.business.ForbiddenOperationException;
import com.bni.orange.wallet.exception.business.MaxMemberReachException;
import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.exception.business.ValidationFailedException;
import com.bni.orange.wallet.model.entity.WalletMember;
import com.bni.orange.wallet.model.entity.read.WalletMemberRead;
import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletMemberStatus;
import com.bni.orange.wallet.model.request.member.WalletMemberInviteRequest;
import com.bni.orange.wallet.model.request.member.WalletMemberUpdateRequest;
import com.bni.orange.wallet.model.response.member.MemberActionResultResponse;
import com.bni.orange.wallet.model.response.member.MemberLimitsResponse;
import com.bni.orange.wallet.model.response.member.WalletMemberDetailResponse;
import com.bni.orange.wallet.repository.WalletMemberRepository;
import com.bni.orange.wallet.repository.WalletRepository;
import com.bni.orange.wallet.repository.read.WalletMemberReadRepository;
import com.bni.orange.wallet.service.command.MembershipCommandService;
import com.bni.orange.wallet.service.query.impl.WalletPolicyQueryServiceImpl;
import com.bni.orange.wallet.utils.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;           
import com.bni.orange.wallet.model.entity.Wallet;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

@Service
@Transactional
public class MembershipCommandServiceImpl implements MembershipCommandService {
  private final ApplicationEventPublisher appEvents;
  private final WalletRepository walletRepo;
  private final WalletMemberRepository memberRepo;               // OLTP
  private final WalletMemberReadRepository memberReadRepo;       // Read
  private final WalletPolicyQueryServiceImpl walletPolicyService;
  public MembershipCommandServiceImpl(WalletRepository walletRepo,
                                      WalletMemberRepository memberRepo,
                                      WalletMemberReadRepository memberReadRepo,
                                      WalletPolicyQueryServiceImpl walletPolicyService,
                                      ApplicationEventPublisher appEvents ) {
                                        
    this.walletRepo = walletRepo;
    this.memberRepo = memberRepo;
    this.memberReadRepo = memberReadRepo;
    this.walletPolicyService = walletPolicyService;
    this.appEvents = appEvents; 
  }

  @Override
  public WalletMemberDetailResponse inviteMember(UUID walletId, WalletMemberInviteRequest req, String idemKey) {
    var actor = requireAdminOrOwner(walletId);
    if (req.getUserId() == null) {
      throw new ValidationFailedException("userId is required. For phone-only, use /wallets/{walletId}/invites");
    }
    var targetUserId = req.getUserId();

    if (Objects.equals(actor.getUserId(), targetUserId)) {
      throw new ConflictException("You cannot invite yourself");
    }


    if (req.getRole() == WalletMemberRole.OWNER) {
      throw new ForbiddenOperationException("Cannot invite as OWNER");
    }
    var policy = walletPolicyService.getWalletPolicy(walletId);
    if (!"SHARED".equalsIgnoreCase(policy.getWalletType())) {
      throw new ForbiddenOperationException("Inviting members is only allowed for SHARED wallets");
    }
    long currentMembers = memberRepo.countByWalletIdAndStatusIn(
        walletId, List.of(WalletMemberStatus.ACTIVE, WalletMemberStatus.INVITED)
    );

    if (currentMembers >= policy.getMaxMembers()) {
      throw new MaxMemberReachException("MAX_MEMBERS_REACHED");
    }

    var wallet=walletRepo.findById(walletId).orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    if (memberRepo.existsByWalletIdAndUserId(walletId, targetUserId)) {
      throw new ConflictException("User is already a member (or invited)");
    }

    var now = OffsetDateTime.now();
    var entity = WalletMember.builder()
        .walletId(walletId)
        .userId(targetUserId)
        .role(req.getRole())
        .status(WalletMemberStatus.INVITED)
        .updatedAt(now)
        .build();

    if (req.getLimits() != null) {
      if (req.getLimits().getDaily() != null)   entity.setDailyLimitRp(req.getLimits().getDaily().longValue());
      if (req.getLimits().getMonthly() != null) entity.setMonthlyLimitRp(req.getLimits().getMonthly().longValue());
    }

    entity = memberRepo.save(entity);
    upsertRead(entity);              
    appEvents.publishEvent(DomainEvents.WalletMemberInvited.builder()
                    .walletId(walletId)
                    .inviterUserId(actor.getUserId())
                    .invitedUserId(entity.getUserId())
                    .role(entity.getRole())
                    .walletName(wallet.getName())
                    .build());
    return toDetailDTO(entity);
  }

  @Override
  public WalletMemberDetailResponse updateMember(UUID walletId, UUID userId, WalletMemberUpdateRequest req) {
    var actor = requireAdminOrOwner(walletId);
    var member = memberRepo.findByWalletIdAndUserId(walletId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

    if (member.getRole() == WalletMemberRole.OWNER && actor.getRole() != WalletMemberRole.OWNER) {
      throw new ForbiddenOperationException("Only OWNER can update another OWNER");
    }

    if (req.getRole() != null) {
      if (member.getRole() == WalletMemberRole.OWNER && req.getRole() != WalletMemberRole.OWNER) {
        long ownerCount = memberRepo.countByWalletIdAndRole(walletId, WalletMemberRole.OWNER);
        if (ownerCount <= 1) throw new ForbiddenOperationException("Cannot demote the last OWNER of the wallet");
      }
      member.setRole(req.getRole());
    }

    if (req.getStatus() != null) {
      member.setStatus(req.getStatus());
    }

    if (req.getLimits() != null) {
      if (req.getLimits().getDaily() != null)   member.setDailyLimitRp(req.getLimits().getDaily().longValue());
      if (req.getLimits().getMonthly() != null) member.setMonthlyLimitRp(req.getLimits().getMonthly().longValue());
    }

    member.setUpdatedAt(OffsetDateTime.now());
    member = memberRepo.save(member); 
    upsertRead(member);               
    return toDetailDTO(member);
  }

  @Override
  public MemberActionResultResponse removeMember(UUID walletId, UUID userId) {
    var actor = requireAdminOrOwner(walletId);
    var member = memberRepo.findByWalletIdAndUserId(walletId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

    if (member.getRole() == WalletMemberRole.OWNER) {
      if (actor.getRole() != WalletMemberRole.OWNER) {
        throw new ForbiddenOperationException("Only OWNER can remove another OWNER");
      }
      long owners = memberRepo.countByWalletIdAndRole(walletId, WalletMemberRole.OWNER);
      if (owners <= 1) throw new ForbiddenOperationException("Cannot remove the last OWNER");
    }

    memberRepo.delete(member);               
    deleteRead(walletId, userId);            

    return MemberActionResultResponse.builder()
        .walletId(walletId)
        .userId(userId)
        .statusAfter(WalletMemberStatus.REMOVED)
        .occurredAt(OffsetDateTime.now())
        .message("Member removed")
        .build();
  }

  @Override
  public MemberActionResultResponse leaveWallet(UUID walletId) {
    var uid = CurrentUser.userId();
    var member = memberRepo.findByWalletIdAndUserId(walletId, uid)
        .orElseThrow(() -> new ResourceNotFoundException("You are not a member"));

    if (member.getRole() == WalletMemberRole.OWNER) {
      long owners = memberRepo.countByWalletIdAndRole(walletId, WalletMemberRole.OWNER);
      if (owners <= 1) throw new ForbiddenOperationException("Owner cannot leave as the last OWNER");
    }

    memberRepo.delete(member);               
    deleteRead(walletId, uid);               

    return MemberActionResultResponse.builder()
        .walletId(walletId)
        .userId(uid)
        .statusAfter(WalletMemberStatus.LEFT)
        .occurredAt(OffsetDateTime.now())
        .message("Left wallet")
        .build();
  }

  private WalletMember requireAdminOrOwner(UUID walletId) {
    var uid = CurrentUser.userId();
    var me = memberRepo.findByWalletIdAndUserId(walletId, uid)
        .orElseThrow(() -> new ForbiddenOperationException("You are not a member of this wallet"));

    if (me.getStatus() != WalletMemberStatus.ACTIVE) {
      throw new ForbiddenOperationException("Only ACTIVE members can perform this action");
    }
    if (me.getRole() != WalletMemberRole.OWNER && me.getRole() != WalletMemberRole.ADMIN) {
      throw new ForbiddenOperationException("Only OWNER/ADMIN can perform this action");
    }
    return me;
  }

  private void upsertRead(WalletMember m) {
    var read = WalletMemberRead.builder()
        .walletId(m.getWalletId())
        .userId(m.getUserId())
        .role(m.getRole())
        .status(m.getStatus())
        .limitCurrency("IDR")
        .dailyLimitRp(m.getDailyLimitRp())
        .monthlyLimitRp(m.getMonthlyLimitRp())
        .updatedAt(m.getUpdatedAt() != null ? m.getUpdatedAt() : OffsetDateTime.now())
        .build();
    memberReadRepo.save(read);
  }

  private void deleteRead(UUID walletId, UUID userId) {
    memberReadRepo.deleteByWalletIdAndUserId(walletId, userId);
  }

  private WalletMemberDetailResponse toDetailDTO(WalletMember m) {
    return WalletMemberDetailResponse.builder()
        .walletId(m.getWalletId())
        .userId(m.getUserId())
        .role(m.getRole())
        .status(m.getStatus())
        .alias(null)
        .invitedBy(null)
        .invitedAt(null)
        .joinedAt(null)
        .updatedAt(m.getUpdatedAt())
        .limits(MemberLimitsResponse.builder()
            .perTransaction(null)
            .daily(m.getDailyLimitRp() != 0 ? BigDecimal.valueOf(m.getDailyLimitRp()) : null)            .weekly(null)
            .monthly(m.getMonthlyLimitRp() != 0 ? BigDecimal.valueOf(m.getMonthlyLimitRp()) : null)
            .currency("IDR")
            .build())
        .metadata(null)
        .build();
  }
}
