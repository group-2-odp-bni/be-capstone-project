package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.enums.PeriodType;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class UserLimitCounterId implements Serializable {
  private UUID userId;
  private PeriodType periodType;
  private OffsetDateTime periodStart;

  public UserLimitCounterId() {}
  public UserLimitCounterId(UUID userId, PeriodType periodType, OffsetDateTime periodStart) {
    this.userId = userId; this.periodType = periodType; this.periodStart = periodStart;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserLimitCounterId that)) return false;
    return Objects.equals(userId, that.userId)
        && periodType == that.periodType
        && Objects.equals(periodStart, that.periodStart);
  }
  @Override public int hashCode() { return Objects.hash(userId, periodType, periodStart); }
}
