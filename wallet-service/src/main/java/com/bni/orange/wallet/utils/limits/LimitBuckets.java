package com.bni.orange.wallet.utils.limits;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
  public static OffsetDateTime dayStart(OffsetDateTime now, ZoneId zone) {
    ZonedDateTime z = now.atZoneSameInstant(zone);
    return z.toLocalDate().atStartOfDay(zone).toOffsetDateTime();
  }
  public static OffsetDateTime weekStart(OffsetDateTime now, ZoneId zone) {
    ZonedDateTime z = now.atZoneSameInstant(zone);
    int dow = z.getDayOfWeek().getValue(); // 1..7
    ZonedDateTime start = z.minusDays(dow - 1L).toLocalDate().atStartOfDay(zone);
    return start.toOffsetDateTime();
  }
  public static OffsetDateTime monthStart(OffsetDateTime now, ZoneId zone) {
    ZonedDateTime z = now.atZoneSameInstant(zone);
    ZonedDateTime first = z.withDayOfMonth(1).toLocalDate().atStartOfDay(zone);
    return first.toOffsetDateTime();
  }
  public static OffsetDateTime dayResetAt(OffsetDateTime now) {
    return dayStart(now).plusDays(1);
  }
  public static OffsetDateTime weekResetAt(OffsetDateTime now) {
    return weekStart(now).plusWeeks(1);
  }
  public static OffsetDateTime monthResetAt(OffsetDateTime now) {
    return monthStart(now).plusMonths(1);
  }

  public static OffsetDateTime dayResetAt(OffsetDateTime now, ZoneId zone) {
    return dayStart(now, zone).plusDays(1);
  }
  public static OffsetDateTime weekResetAt(OffsetDateTime now, ZoneId zone) {
    return weekStart(now, zone).plusWeeks(1);
  }
  public static OffsetDateTime monthResetAt(OffsetDateTime now, ZoneId zone) {
    return monthStart(now, zone).plusMonths(1);
  }
  public static ZoneId safeZone(String tz) {
    try {
      if (tz == null || tz.isBlank()) return ZONE;
      return ZoneId.of(tz);
    } catch (Exception ignored) {
      return ZONE;
    }
  }
}
