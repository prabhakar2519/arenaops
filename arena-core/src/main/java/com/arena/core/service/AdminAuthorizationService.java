package com.arena.core.service;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {

  public boolean isAdmin(Jwt jwt) {
    return hasRealmRole(jwt, "ADMIN");
  }

  public String resolveUsername(Jwt jwt) {
    String username = jwt.getClaimAsString("preferred_username");
    return username == null || username.isBlank() ? jwt.getSubject() : username;
  }

  private boolean hasRealmRole(Jwt jwt, String role) {
    if (jwt == null) return false;
    Object realmAccessClaim = jwt.getClaim("realm_access");
    if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
      return false;
    }

    Object rolesClaim = realmAccess.get("roles");
    if (!(rolesClaim instanceof Collection<?> roles)) {
      return false;
    }

    return roles.stream().map(String::valueOf).anyMatch(existingRole -> existingRole.equals(role));
  }
}
