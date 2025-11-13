package com.bni.orange.wallet.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class Jsons {
  private static final ObjectMapper om = new ObjectMapper();
  private Jsons() {}
  public static String toJson(Object o) {
    try { return om.writeValueAsString(o); } catch (Exception e) { return "{}"; }
  }
}
