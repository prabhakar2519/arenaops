package com.arena.login.controller;

import com.arena.login.service.TokenService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionCheckoutProxyTest {
 @Test void staleQuoteCodeSurvivesBffWithSanitizedDescription() throws Exception {
  var core=mock(RestTemplate.class);
  var controller=new TokenController(mock(TokenService.class),core);
  ReflectionTestUtils.setField(controller,"coreUrl","http://example.invalid");
  var request=new MockHttpServletRequest("POST","/api/academy/running-sessions/1/stop");
  request.setContentType("application/json");request.setContent("{\"quoteToken\":\"test\"}".getBytes(StandardCharsets.UTF_8));
  var session=new MockHttpSession();session.setAttribute("ACCESS_TOKEN","unit");session.setAttribute("LAST_PRESENCE_TOUCH",System.currentTimeMillis());
  when(core.exchange(any(URI.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(byte[].class)))
   .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT,"conflict",HttpHeaders.EMPTY,"{\"errorCode\":\"SESSION_QUOTE_STALE\",\"description\":\"unsafe upstream detail\"}".getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8));
  var response=controller.proxyRequest(request,session);
  var body=new String(response.getBody(),StandardCharsets.UTF_8);
  assertEquals(409,response.getStatusCode().value());assertTrue(body.contains("SESSION_QUOTE_STALE"));assertFalse(body.contains("unsafe upstream"));
 }
}
