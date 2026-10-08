package com.arena.login.service;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RealmRoleTest {
  @Test void allApplicationRolesAreRecognizedWithoutUsernamePrivileges() {
    var service = new TokenService();
    for (String role : new String[]{"ADMIN","OWNER","COACH","STAFF","USER"}) {
      String payload = "{\"sub\":\"admin\",\"preferred_username\":\"admin\",\"realm_access\":{\"roles\":[\""+role+"\"]}}";
      String token = "test." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".test";
      assertEquals(role, service.getUserInfoFromToken(token).getRole());
    }
  }
}
