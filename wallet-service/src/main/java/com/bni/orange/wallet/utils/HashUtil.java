package com.bni.orange.wallet.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashUtil {
    public static String canonicalJson(ObjectMapper om, Object o){
        try { return om.writer().withDefaultPrettyPrinter().writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    public static String sha256(String s){
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var sb = new StringBuilder();
            for (byte b: md.digest(s.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e){ throw new RuntimeException(e); }
    }
}
