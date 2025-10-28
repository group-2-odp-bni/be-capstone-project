package com.bni.orange.wallet.utils.metadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public final class MetadataFilter {
  private MetadataFilter(){}
  private static final Set<String> ALLOWED = Set.of("color","icon","notes","tags");

  public static Map<String,Object> filter(ObjectMapper om, String jsonOrNull) {
    if (jsonOrNull == null || jsonOrNull.isBlank()) return Map.of();
    try {
      Map<String,Object> src = om.readValue(jsonOrNull, new TypeReference<Map<String,Object>>() {});
      Map<String,Object> out = new LinkedHashMap<>();
      for (String key : ALLOWED) if (src.containsKey(key)) out.put(key, src.get(key));
      return out;
    } catch (Exception e) {
      return Map.of();
    }
  }
}
