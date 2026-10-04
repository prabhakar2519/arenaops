package com.arena.login.service;

import com.arena.login.exception.ArenaOpsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TokenServicePkceTest {
  @Test
  void verifierIsSentToTheSelectedInternalRealmEndpoint() {
    for (String realm : new String[]{"arena-dev", "arena-sit", "arena"}) {
      var service = new TokenService();
      ReflectionTestUtils.setField(service, "serverUrl", "http://keycloak:8080/auth");
      ReflectionTestUtils.setField(service, "realm", realm);
      ReflectionTestUtils.setField(service, "clientId", "arena-ui");
      var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
      server.expect(requestTo("http://keycloak:8080/auth/realms/" + realm + "/protocol/openid-connect/token"))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=" + "v".repeat(43))))
          .andRespond(withSuccess("{\"access_token\":\"test-access\",\"expires_in\":300,\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
      assertEquals("test-access", service.exchangeCodeForToken("test-code", "https://ui.test/login/callback", "v".repeat(43)).getAccessToken());
      server.verify();
    }
  }

  @Test
  void invalidOrMissingVerifierFailsBeforeContactingIdentityProvider() {
    var service = new TokenService();
    for (String verifier : new String[]{null, "", "too-short"}) {
      assertThrows(ArenaOpsException.class, () -> service.exchangeCodeForToken("test-code", "https://ui.test/login/callback", verifier));
    }
  }
}
