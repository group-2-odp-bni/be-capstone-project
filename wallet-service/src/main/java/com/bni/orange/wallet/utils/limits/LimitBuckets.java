package com.bni.orange.wallet.utils.limits;
import java.time.OffsetDateTime;
import java.time.ZoneId;
public final class LimitBuckets {
  private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

  public static OffsetDateTime dayStart(OffsetDateTime now) {
    return now.atZoneSameInstant(ZONE).toLocalDate().atStartOfDay(ZONE).toOffsetDateTime();
  }
  public static OffsetDateTime weekStart(OffsetDateTime now) {
    var zdt = now.atZoneSameInstant(ZONE);
    var dow = zdt.getDayOfWeek().getValue();
    var start = zdt.minusDays(dow-1).toLocalDate().atStartOfDay(ZONE);
    return start.toOffsetDateTime();
  }
  public static OffsetDateTime monthStart(OffsetDateTime now) {
    var zdt = now.atZoneSameInstant(ZONE);
    var first = zdt.withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE);
    return first.toOffsetDateTime();
  }
}
