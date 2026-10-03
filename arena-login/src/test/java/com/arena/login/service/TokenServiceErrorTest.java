package com.arena.login.service;

import com.arena.login.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenServiceErrorTest {
  @Test
  void identityRejectionAndUnavailableProviderHaveDifferentCodesWithoutRawBodies() {
    TokenService service = new TokenService();
    ReflectionTestUtils.setField(service, "serverUrl", "http://identity.test");
    ReflectionTestUtils.setField(service, "realm", "test");
    ReflectionTestUtils.setField(service, "clientId", "test");
    var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
    server.expect(anything()).andRespond(withBadRequest().body("client_secret=private-token"));
    var denied = assertThrows(ArenaOpsException.class, () -> service.exchangeCodeForToken("code", "http://ui.test/callback"));
    assertEquals(ErrorCode.AUTHENTICATION_FAILED, denied.getErrorCode());
    assertFalse(denied.getMessage().contains("private-token"));
    server.verify();
    server.reset();
    server.expect(anything()).andRespond(withServerError().body("internal-host SQL"));
    var unavailable = assertThrows(ArenaOpsException.class, () -> service.refreshToken("test-refresh"));
    assertEquals(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE, unavailable.getErrorCode());
    server.verify();
  }
}
