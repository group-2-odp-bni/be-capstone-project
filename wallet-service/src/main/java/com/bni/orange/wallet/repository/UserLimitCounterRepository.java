package com.bni.orange.wallet.repository;

import com.bni.orange.wallet.model.entity.UserLimitCounter;
import com.bni.orange.wallet.model.entity.UserLimitCounterId;
import com.bni.orange.wallet.model.enums.PeriodType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserLimitCounterRepository extends JpaRepository<UserLimitCounter, UserLimitCounterId> {
  @Query(value = """
      SELECT COALESCE(ulc.amount_used_rp, 0)
      FROM wallet_oltp.user_limit_counters ulc
      WHERE ulc.user_id = :userId
        AND ulc.period_type = CAST(:period AS domain.period_type)
        AND ulc.period_start = :periodStart
      """, nativeQuery = true)
  Long findUsedNative(@Param("userId") UUID userId,
                      @Param("period") String period,
                      @Param("periodStart") OffsetDateTime periodStart);

  default long findUsed(UUID userId, PeriodType period, OffsetDateTime start) {
    return Optional.ofNullable(findUsedNative(userId, period.name(), start)).orElse(0L);
  }

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
    INSERT INTO wallet_oltp.user_limit_counters
      (user_id, period_type, period_start, amount_used_rp, updated_at, created_at)
    VALUES
      (:userId, CAST(:period AS domain.period_type), :periodStart, :delta, now(), now())
    ON CONFLICT (user_id, period_type, period_start)
    DO UPDATE SET amount_used_rp = wallet_oltp.user_limit_counters.amount_used_rp + EXCLUDED.amount_used_rp,
                  updated_at = now()
  """, nativeQuery = true)
  int addUsage(@Param("userId") UUID userId,
               @Param("period") String period,                  // "DAY"/"WEEK"/"MONTH"
               @Param("periodStart") OffsetDateTime periodStart,
               @Param("delta") long delta);
}
