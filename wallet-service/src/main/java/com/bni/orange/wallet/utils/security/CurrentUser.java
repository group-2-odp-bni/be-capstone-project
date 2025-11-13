package com.bni.orange.wallet.utils.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class CurrentUser {
  private CurrentUser(){}

  public static UUID userId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) throw new IllegalStateException("No authentication");
    Object principal = auth.getPrincipal();
    if (principal instanceof Jwt jwt) {
      String raw = jwt.getClaimAsString("user_id");
      if (raw == null || raw.isBlank()) raw = jwt.getSubject();
      return UUID.fromString(raw);
    }
    return UUID.fromString(String.valueOf(principal));
  }
}
