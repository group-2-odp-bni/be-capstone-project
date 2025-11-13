package com.bni.orange.wallet.model.request.limits;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserLimitsUpdateRequest {
  @Min(0) private long perTxMinRp;
  @Min(0) private long perTxMaxRp;
  @Min(0) private long dailyMaxRp;
  @Min(0) private long weeklyMaxRp;
  @Min(0) private long monthlyMaxRp;

  private boolean enforcePerTx;
  private boolean enforceDaily;
  private boolean enforceWeekly;
  private boolean enforceMonthly;

  private OffsetDateTime effectiveFrom;
  private OffsetDateTime effectiveThrough;

  @NotBlank private String timezone;
}
