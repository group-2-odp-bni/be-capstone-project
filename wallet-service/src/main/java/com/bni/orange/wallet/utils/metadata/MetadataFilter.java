package com.bni.orange.wallet.utils.metadata;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MetadataFilter {
    private MetadataFilter() {}
    private static final Set<String> ALLOWED = Set.of("colors", "icon", "notes", "tags", "theme");

    public static Map<String, Object> filter(Map<String, Object> mapOrNull) {
        
        if (mapOrNull == null || mapOrNull.isEmpty()) {
            return Map.of();
        }
        
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : ALLOWED) {
                if (mapOrNull.containsKey(key)) {
                    out.put(key, mapOrNull.get(key));
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }
}