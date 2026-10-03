package com.arena.login.controller;

import com.arena.login.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreUnavailableLoginTest {
  @Test
  void gatewayPreservesRecognizedCoreErrorCodesAndRedactsUnstructuredBodies() throws Exception {
    RestTemplate core = mock(RestTemplate.class);
    TokenController controller = new TokenController(mock(TokenService.class), core);
    ReflectionTestUtils.setField(controller, "coreUrl", "http://localhost:7701");
    var session = new MockHttpSession();
    session.setAttribute("ACCESS_TOKEN", "test-token");
    session.setAttribute("LAST_PRESENCE_TOUCH", java.time.Instant.now().getEpochSecond());
    var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/admin/customers");
    request.addHeader("X-Correlation-ID", "12345678-1234-1234-1234-123456789abc");
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    for (String body : new String[]{"{\"errorCode\":\"CUSTOMER_ALREADY_EXISTS\",\"description\":\"private details\"}",
        "SQL constraint exception client_secret=secret"}) {
      when(core.exchange(any(java.net.URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class)))
          .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
              body.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8));
      var response = controller.proxyRequest(request, session);
      assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
      var error = mapper.readTree(response.getBody());
      assertEquals(body.startsWith("{") ? "CUSTOMER_ALREADY_EXISTS" : "CONFLICT", error.path("errorCode").asText());
      assertEquals("12345678-1234-1234-1234-123456789abc", error.path("correlationId").asText());
      assertEquals("/api/admin/customers", error.path("path").asText());
      assertFalse(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("private details"));
      assertFalse(new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8).contains("client_secret"));
    }
  }

  @Test
  void readinessChecksCoreWithoutRequiringAnAuthenticatedSession() {
    RestTemplate core = mock(RestTemplate.class);
    TokenController controller = new TokenController(mock(TokenService.class), core);
    ReflectionTestUtils.setField(controller, "coreUrl", "http://localhost:7701");
    when(core.getForEntity("http://localhost:7701/api/health", byte[].class))
        .thenReturn(ResponseEntity.ok(new byte[0]))
        .thenThrow(new ResourceAccessException("Connection refused"));
    assertEquals(HttpStatus.OK, controller.readiness(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/readiness")).getStatusCode());
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.readiness(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/readiness")).getStatusCode());
  }

  @Test
  void successfulIdentityLoginDoesNotSucceedWhenCoreIsDown() {
    RestTemplate core = mock(RestTemplate.class);
    TokenService tokens = mock(TokenService.class);
    TokenController controller = new TokenController(tokens, core);
    ReflectionTestUtils.setField(controller, "coreUrl", "http://localhost:7701");
    when(tokens.exchangeCodeForToken("test-code", "http://localhost/login/callback"))
        .thenReturn(com.arena.login.model.TokenResponse.builder().accessToken("test-token").build());
    when(core.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
        .thenThrow(new ResourceAccessException("Connection refused"));
    MockHttpSession session = new MockHttpSession();
    var request = new com.arena.login.model.TokenRequest("test-code", "http://localhost/login/callback");
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.validateToken(request, session, new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/token")).getStatusCode());
    assertTrue(session.isInvalid());
  }

  @Test
  void coreConnectionFailureAndServerErrorsReturn503WithoutDestroyingExistingSession() {
    for (RuntimeException failure : new RuntimeException[]{
        new ResourceAccessException("Connection refused"),
        new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)}) {
      RestTemplate core = mock(RestTemplate.class);
      TokenController controller = new TokenController(mock(TokenService.class), core);
      ReflectionTestUtils.setField(controller, "coreUrl", "http://localhost:7701");
      when(core.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
          .thenThrow(failure);
      MockHttpSession session = new MockHttpSession();
      session.setAttribute("ACCESS_TOKEN", "test-token");
      var response = controller.getUserInfo(session, new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/user"));
      assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
      assertTrue(response.getBody().toString().contains("temporarily unavailable"));
      assertFalse(session.isInvalid());
    }
  }

  @Test
  void actualAccessDenialRemains403AndInvalidatesSession() {
    RestTemplate core = mock(RestTemplate.class);
    TokenController controller = new TokenController(mock(TokenService.class), core);
    ReflectionTestUtils.setField(controller, "coreUrl", "http://localhost:7701");
    when(core.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("ACCESS_TOKEN", "test-token");
    assertEquals(HttpStatus.FORBIDDEN, controller.getUserInfo(session, new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/user")).getStatusCode());
    assertTrue(session.isInvalid());
  }
}
