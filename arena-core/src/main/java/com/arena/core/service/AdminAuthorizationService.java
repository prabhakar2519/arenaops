package com.arena.core.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {

  private final Set<String> adminUsernames;

  public AdminAuthorizationService(@Value("${app.admin.usernames:arena_admin}") String adminUsernamesConfig) {
    this.adminUsernames = Arrays.stream(adminUsernamesConfig.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> value.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }

  public boolean isAdmin(Jwt jwt) {
    return hasRealmRole(jwt, "ADMIN") || adminUsernames.contains(resolveUsername(jwt).toLowerCase(Locale.ROOT));
  }

  public String resolveUsername(Jwt jwt) {
    String username = jwt.getClaimAsString("preferred_username");
    return username == null || username.isBlank() ? jwt.getSubject() : username;
  }

  private boolean hasRealmRole(Jwt jwt, String role) {
    Object realmAccessClaim = jwt.getClaim("realm_access");
    if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
      return false;
    }

    Object rolesClaim = realmAccess.get("roles");
    if (!(rolesClaim instanceof Collection<?> roles)) {
      return false;
    }

    return roles.stream().map(String::valueOf).anyMatch(existingRole -> existingRole.equalsIgnoreCase(role));
  }
}
