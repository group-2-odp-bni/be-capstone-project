package com.bni.orange.wallet.utils;

public class PhoneUtil {
      private PhoneUtil() {}
  public static String normalizeToE164(String raw) {
    if (raw == null) return null;
    String p = raw.replaceAll("[^\\d+]", "");
    if (!p.startsWith("+")) {
      if (p.startsWith("0")) p = "+62" + p.substring(1);
      else p = "+62" + p;
    }
    return p;
  }
}
