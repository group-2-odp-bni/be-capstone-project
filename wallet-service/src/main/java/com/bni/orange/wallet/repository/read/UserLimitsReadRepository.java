package com.bni.orange.wallet.repository.read;

import com.bni.orange.wallet.model.entity.read.UserLimitsRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface UserLimitsReadRepository extends JpaRepository<UserLimitsRead, UUID> {

  Optional<UserLimitsRead> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);

  @Transactional @Modifying
  @Query("""
      UPDATE UserLimitsRead u
         SET perTxMaxRp   = :perTxMaxRp,
             dailyMaxRp   = :dailyMaxRp,
             weeklyMaxRp  = :weeklyMaxRp,
             monthlyMaxRp = :monthlyMaxRp,
             perTxMinRp   = :perTxMinRp,
             enforcePerTx = :enforcePerTx,
             enforceDaily = :enforceDaily,
             enforceWeekly= :enforceWeekly,
             enforceMonthly=:enforceMonthly,
             effectiveFrom= :effectiveFrom,
             effectiveThrough = :effectiveThrough,
             timezone     = :timezone,
             updatedAt    = CURRENT_TIMESTAMP
       WHERE u.userId = :userId
      """)
  int mirrorUpdate(UUID userId,
                   long perTxMaxRp, long dailyMaxRp, long weeklyMaxRp, long monthlyMaxRp, long perTxMinRp,
                   boolean enforcePerTx, boolean enforceDaily, boolean enforceWeekly, boolean enforceMonthly,
                   java.time.OffsetDateTime effectiveFrom, java.time.OffsetDateTime effectiveThrough,
                   String timezone);
}
