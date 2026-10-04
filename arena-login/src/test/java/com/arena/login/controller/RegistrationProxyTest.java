package com.arena.login.controller;

import com.arena.login.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationProxyTest {
  @Test
  void allowsInvitationPostsWithoutSessionButProtectsAdminAndOtherMethods() throws Exception {
    RestTemplate core = mock(RestTemplate.class);
    TokenController controller = new TokenController(mock(TokenService.class), core);
    ReflectionTestUtils.setField(controller, "coreUrl", "http://localhost:7701");
    when(core.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(new byte[0]));
    for (String path : new String[]{"/api/registration/activate", "/api/registration/validate-invitation"}) {
      assertEquals(HttpStatus.OK, controller.proxyRequest(new MockHttpServletRequest("POST", path), new MockHttpSession()).getStatusCode());
      assertEquals(HttpStatus.UNAUTHORIZED, controller.proxyRequest(new MockHttpServletRequest("GET", path), new MockHttpSession()).getStatusCode());
    }
    assertEquals(HttpStatus.UNAUTHORIZED, controller.proxyRequest(new MockHttpServletRequest("POST", "/api/admin/customers"), new MockHttpSession()).getStatusCode());
    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, controller.proxyRequest(new MockHttpServletRequest("PUT", "/api/user"), new MockHttpSession()).getStatusCode());
    verify(core, times(2)).exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class));
  }
}
