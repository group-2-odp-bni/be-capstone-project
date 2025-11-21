package com.bni.orange.wallet.service.command.initializer;
import jakarta.transaction.Transactional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.bni.orange.wallet.repository.UserLimitsRepository;
import com.bni.orange.wallet.repository.read.UserLimitsReadRepository;
import com.bni.orange.wallet.model.entity.UserLimits;
import com.bni.orange.wallet.model.entity.read.UserLimitsRead;

import java.time.OffsetDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(LimitsDefaultsProperties.class)
public class UserLimitsInitializer {

    private final UserLimitsRepository oltpRepo;
    private final UserLimitsReadRepository readRepo;
    private final LimitsDefaultsProperties defaults; 

    @Transactional
    public void ensureDefaultsForUser(UUID userId) {
        var oltpExists = oltpRepo.existsByUserId(userId);
        UserLimits entity;

        if (!oltpExists) {
            var now = OffsetDateTime.now();
            entity = UserLimits.builder()
                .userId(userId)
                .perTxMinRp(defaults.getPerTxMinRp())
                .perTxMaxRp(defaults.getPerTxMaxRp())
                .dailyMaxRp(defaults.getDailyMaxRp())
                .weeklyMaxRp(defaults.getWeeklyMaxRp())
                .monthlyMaxRp(defaults.getMonthlyMaxRp())
                .enforcePerTx(defaults.isEnforcePerTx())
                .enforceDaily(defaults.isEnforceDaily())
                .enforceWeekly(defaults.isEnforceWeekly())
                .enforceMonthly(defaults.isEnforceMonthly())
                .effectiveFrom(now)
                .effectiveThrough(null)
                .timezone(defaults.getTimezone())
                .build();
            oltpRepo.save(entity);
        } else {
            entity = oltpRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("OLTP data inconsistency"));
        }

        var readExists = readRepo.existsByUserId(userId);
        if (!readExists) {
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
    }
}
