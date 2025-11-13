package com.bni.orange.wallet.service.command.impl;

import com.bni.orange.wallet.model.entity.UserLimits;
import com.bni.orange.wallet.model.entity.read.UserLimitsRead;
import com.bni.orange.wallet.model.request.limits.UserLimitsUpdateRequest;
import com.bni.orange.wallet.model.response.limits.UserLimitsResponse;
import com.bni.orange.wallet.repository.UserLimitsRepository;
import com.bni.orange.wallet.repository.read.UserLimitsReadRepository;
import com.bni.orange.wallet.service.command.LimitsCommandService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LimitsCommandServiceImpl implements LimitsCommandService {

  private final UserLimitsRepository oltpRepo;
  private final UserLimitsReadRepository readRepo;

  @Override
  @Transactional
  public UserLimitsResponse updateMyLimits(UserLimitsUpdateRequest req) {
    UUID userId = currentUserId();

    if (req.getPerTxMinRp() > req.getPerTxMaxRp()) {
      throw new IllegalArgumentException("perTxMinRp must be <= perTxMaxRp");
    }
    if (req.getDailyMaxRp() < req.getPerTxMaxRp()) {
      throw new IllegalArgumentException("dailyMaxRp must be >= perTxMaxRp");
    }
    if (req.getWeeklyMaxRp() < req.getDailyMaxRp()) {
      throw new IllegalArgumentException("weeklyMaxRp must be >= dailyMaxRp");
    }
    if (req.getMonthlyMaxRp() < req.getWeeklyMaxRp()) {
      throw new IllegalArgumentException("monthlyMaxRp must be >= weeklyMaxRp");
    }
    try { ZoneId.of(req.getTimezone()); } catch (Exception e) {
      throw new IllegalArgumentException("Invalid timezone: " + req.getTimezone());
    }

    var entity = oltpRepo.findByUserId(userId)
        .orElse(UserLimits.builder().userId(userId).build());

    entity.setPerTxMaxRp(req.getPerTxMaxRp());
    entity.setDailyMaxRp(req.getDailyMaxRp());
    entity.setWeeklyMaxRp(req.getWeeklyMaxRp());
    entity.setMonthlyMaxRp(req.getMonthlyMaxRp());
    entity.setPerTxMinRp(req.getPerTxMinRp());

    entity.setEnforcePerTx(req.isEnforcePerTx());
    entity.setEnforceDaily(req.isEnforceDaily());
    entity.setEnforceWeekly(req.isEnforceWeekly());
    entity.setEnforceMonthly(req.isEnforceMonthly());

    entity.setEffectiveFrom(req.getEffectiveFrom());
    entity.setEffectiveThrough(req.getEffectiveThrough());
    entity.setTimezone(req.getTimezone());

    oltpRepo.save(entity);

    int updated = readRepo.mirrorUpdate(
        userId,
        entity.getPerTxMaxRp(), entity.getDailyMaxRp(), entity.getWeeklyMaxRp(),
        entity.getMonthlyMaxRp(), entity.getPerTxMinRp(),
        entity.isEnforcePerTx(), entity.isEnforceDaily(),
        entity.isEnforceWeekly(), entity.isEnforceMonthly(),
        entity.getEffectiveFrom(), entity.getEffectiveThrough(),
        entity.getTimezone()
    );

    if (updated == 0) {
      var mirror = UserLimitsRead.builder()
          .userId(userId)
          .perTxMaxRp(entity.getPerTxMaxRp())
          .dailyMaxRp(entity.getDailyMaxRp())
          .weeklyMaxRp(entity.getWeeklyMaxRp())
          .monthlyMaxRp(entity.getMonthlyMaxRp())
          .perTxMinRp(entity.getPerTxMinRp())
          .enforcePerTx(entity.isEnforcePerTx())
          .enforceDaily(entity.isEnforceDaily())
          .enforceWeekly(entity.isEnforceWeekly())
          .enforceMonthly(entity.isEnforceMonthly())
          .effectiveFrom(entity.getEffectiveFrom())
          .effectiveThrough(entity.getEffectiveThrough())
          .timezone(entity.getTimezone())
          .build();
      readRepo.save(mirror);
    }

    var m = readRepo.findByUserId(userId).orElseThrow();
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
