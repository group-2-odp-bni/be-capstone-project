package com.bni.orange.wallet.service.command.initializer;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.bni.orange.wallet.repository.UserLimitsRepository;
import com.bni.orange.wallet.repository.read.UserLimitsReadRepository;
import com.bni.orange.wallet.model.entity.UserLimits;
import java.time.OffsetDateTime;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class UserLimitsInitializer {

    private final UserLimitsRepository oltpRepo;
    private final UserLimitsReadRepository readRepo;
    private final LimitsDefaultsProperties defaults; 

    @Transactional
    public void ensureDefaultsForUser(UUID userId) {
        if (oltpRepo.existsByUserId(userId)) {
            return;
        }

        var now = OffsetDateTime.now();

        var entity = UserLimits.builder()
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

        readRepo.mirrorUpdate(
            userId,
            entity.getPerTxMaxRp(),
            entity.getDailyMaxRp(),
            entity.getWeeklyMaxRp(),
            entity.getMonthlyMaxRp(),
            entity.getPerTxMinRp(),
            entity.isEnforcePerTx(),
            entity.isEnforceDaily(),
            entity.isEnforceWeekly(),
            entity.isEnforceMonthly(),
            entity.getEffectiveFrom(),
            entity.getEffectiveThrough(),
            entity.getTimezone()
        );
    }
}
