package com.bni.orange.wallet.service.query.impl;

import com.bni.orange.wallet.model.entity.read.UserLimitsRead;
import com.bni.orange.wallet.model.enums.PeriodType;
import com.bni.orange.wallet.model.response.limits.UserLimitsResponse;
import com.bni.orange.wallet.repository.read.UserLimitsReadRepository;
import com.bni.orange.wallet.service.command.LimitCounterService;
import com.bni.orange.wallet.service.command.initializer.UserLimitsInitializer;
import com.bni.orange.wallet.service.query.LimitsQueryService;
import com.bni.orange.wallet.utils.limits.LimitBuckets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LimitsQueryServiceImpl implements LimitsQueryService {

  private final UserLimitsReadRepository readRepo;
  private final LimitCounterService limitCounterService;
  private final UserLimitsInitializer limitsInitializer;

  @Override
  @Transactional
  public UserLimitsResponse getMyLimits() {
    UUID userId = currentUserId();

    // Defensive programming: Auto-initialize if not found
    UserLimitsRead m = readRepo.findByUserId(userId)
        .orElseGet(() -> {
            log.warn("User limits not found for userId={}, auto-initializing...", userId);
            limitsInitializer.ensureDefaultsForUser(userId);
            return readRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Failed to initialize user limits for userId=" + userId));
        });
    var zone = (m.getTimezone() == null || m.getTimezone().isBlank())
        ? ZoneId.of("Asia/Jakarta")
        : ZoneId.of(m.getTimezone());

    var now = OffsetDateTime.now();
    var dayStart   = LimitBuckets.dayStart(now, zone);
    var weekStart  = LimitBuckets.weekStart(now, zone);
    var monthStart = LimitBuckets.monthStart(now, zone);

    long usedDay   = limitCounterService.getUsed(userId, PeriodType.DAY,   dayStart);
    long usedWeek  = limitCounterService.getUsed(userId, PeriodType.WEEK,  weekStart);
    long usedMonth = limitCounterService.getUsed(userId, PeriodType.MONTH, monthStart);

    Long dailyRemaining   = (m.isEnforceDaily()   && m.getDailyMaxRp()   > 0) ? Math.max(0L, m.getDailyMaxRp()   - usedDay)   : null;
    Long weeklyRemaining  = (m.isEnforceWeekly()  && m.getWeeklyMaxRp()  > 0) ? Math.max(0L, m.getWeeklyMaxRp()  - usedWeek)  : null;
    Long monthlyRemaining = (m.isEnforceMonthly() && m.getMonthlyMaxRp() > 0) ? Math.max(0L, m.getMonthlyMaxRp() - usedMonth) : null;



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
        .dailyUsedRp(usedDay)
        .weeklyUsedRp(usedWeek)
        .monthlyUsedRp(usedMonth)
        .dailyRemainingRp(dailyRemaining)
        .weeklyRemainingRp(weeklyRemaining)
        .monthlyRemainingRp(monthlyRemaining)
        .dailyResetAt(LimitBuckets.dayResetAt(now, zone))
        .weeklyResetAt(LimitBuckets.weekResetAt(now, zone))
        .monthlyResetAt(LimitBuckets.monthResetAt(now, zone))
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
