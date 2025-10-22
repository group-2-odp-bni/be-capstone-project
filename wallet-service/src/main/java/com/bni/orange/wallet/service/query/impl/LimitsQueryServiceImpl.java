package com.bni.orange.wallet.service.query.impl;

import com.bni.orange.wallet.model.entity.read.UserLimitsRead;
import com.bni.orange.wallet.model.response.limits.UserLimitsResponse;
import com.bni.orange.wallet.repository.read.UserLimitsReadRepository;
import com.bni.orange.wallet.service.query.LimitsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LimitsQueryServiceImpl implements LimitsQueryService {

  private final UserLimitsReadRepository readRepo;

  @Override
  @Transactional(readOnly = true)
  public UserLimitsResponse getMyLimits() {
    UUID userId = currentUserId();

    UserLimitsRead m = readRepo.findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("User limits not found"));

    return UserLimitsResponse.builder()
        .perTxMinRp(m.getPerTxMinRp())
        .perTxMaxRp(m.getPerTxMaxRp())
        .dailyMaxRp(m.getDailyMaxRp())
        .weeklyMaxRp(m.getWeeklyMaxRp())
        .monthlyMaxRp(m.getMonthlyMaxRp())
        .enforcePerTx(m.isEnforcePerTx())
        .enforceDaily(m.isEnforceDaily())
        .enforceWeekly(m.isEnforceWeekly())
        .enforceMonthly(m.isEnforceMonthly())
        .effectiveFrom(m.getEffectiveFrom())
        .effectiveThrough(m.getEffectiveThrough())
        .updatedAt(m.getUpdatedAt())
        .timezone(m.getTimezone())
        .build();
  }

  private UUID currentUserId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
      return UUID.fromString(jwt.getClaimAsString("sub"));
    }
    throw new AccessDeniedException("Unauthenticated");
  }
}
