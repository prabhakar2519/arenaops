package com.arena.core.service;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
class AdminAuthorizationServiceTest {
  @Test void onlyExactAdminRealmRoleGrantsAdministration() {
    var service = new AdminAuthorizationService();
    for (String role : List.of("ADMIN", "OWNER", "COACH", "STAFF", "admin")) {
      var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("master-admin")
          .claim("preferred_username", "admin").claim("realm_access", Map.of("roles", List.of(role))).build();
      assertEquals(role.equals("ADMIN"), service.isAdmin(jwt));
      if (role.equals("ADMIN")) {
        var users = org.mockito.Mockito.mock(com.arena.core.repository.AppUserRepository.class);
        var access = org.mockito.Mockito.mock(CustomerAccessService.class);
        var controller = new com.arena.core.controller.AccessController(users, service, access);
        assertEquals(true, controller.check(jwt).getBody().get("allowed"));
        org.mockito.Mockito.verifyNoInteractions(users, access);
      }
    }
    assertFalse(service.isAdmin(Jwt.withTokenValue("test").header("alg", "none").subject("admin").build()));
    assertFalse(service.isAdmin(null));
  }
}
