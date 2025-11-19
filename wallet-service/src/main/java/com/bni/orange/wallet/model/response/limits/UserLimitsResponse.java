package com.bni.orange.wallet.model.response.limits;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserLimitsResponse {
  private long perTxMinRp, perTxMaxRp, dailyMaxRp, weeklyMaxRp, monthlyMaxRp;
  private boolean enforcePerTx, enforceDaily, enforceWeekly, enforceMonthly;
  private OffsetDateTime effectiveFrom, effectiveThrough, updatedAt;
  private String timezone;

  private Long dailyUsedRp, weeklyUsedRp, monthlyUsedRp;
  private Long dailyRemainingRp, weeklyRemainingRp, monthlyRemainingRp;
  private OffsetDateTime dailyResetAt, weeklyResetAt, monthlyResetAt;

}
