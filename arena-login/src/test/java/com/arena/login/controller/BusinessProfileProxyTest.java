package com.arena.login.controller;
import com.arena.login.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class BusinessProfileProxyTest {
 @Test void multipartBodyAndContentTypeAreForwardedWithoutParsing()throws Exception{
  var core=mock(RestTemplate.class);var controller=new TokenController(mock(TokenService.class),core);ReflectionTestUtils.setField(controller,"coreUrl","http://example.invalid");
  byte[] bytes="--test-boundary\r\nContent-Disposition: form-data; name=profile\r\n\r\n{}\r\n--test-boundary--".getBytes();
  var request=new MockHttpServletRequest("PUT","/api/business-profile");request.setContentType("multipart/form-data; boundary=test-boundary");request.setContent(bytes);var session=new MockHttpSession();session.setAttribute("ACCESS_TOKEN","unit-test-placeholder");session.setAttribute("LAST_PRESENCE_TOUCH",System.currentTimeMillis());
  when(core.exchange(any(URI.class),eq(HttpMethod.PUT),any(HttpEntity.class),eq(byte[].class))).thenReturn(ResponseEntity.ok("{}".getBytes()));
  assertEquals(HttpStatus.OK,controller.proxyRequest(request,session).getStatusCode());var entity=org.mockito.ArgumentCaptor.forClass(HttpEntity.class);verify(core).exchange(any(URI.class),eq(HttpMethod.PUT),entity.capture(),eq(byte[].class));assertArrayEquals(bytes,(byte[])entity.getValue().getBody());assertEquals("multipart/form-data; boundary=test-boundary",entity.getValue().getHeaders().getFirst("Content-Type"));
 }
 @Test void invalidLogoErrorSurvivesBffSafeErrorMapping()throws Exception{
  var core=mock(RestTemplate.class);var controller=new TokenController(mock(TokenService.class),core);ReflectionTestUtils.setField(controller,"coreUrl","http://example.invalid");var request=new MockHttpServletRequest("PUT","/api/business-profile");var session=new MockHttpSession();session.setAttribute("ACCESS_TOKEN","unit-test-placeholder");session.setAttribute("LAST_PRESENCE_TOUCH",System.currentTimeMillis());
  when(core.exchange(any(URI.class),eq(HttpMethod.PUT),any(HttpEntity.class),eq(byte[].class))).thenThrow(org.springframework.web.client.HttpClientErrorException.create(HttpStatus.BAD_REQUEST,"Bad request",HttpHeaders.EMPTY,"{\"errorCode\":\"INVALID_LOGO\"}".getBytes(),java.nio.charset.StandardCharsets.UTF_8));
  var response=controller.proxyRequest(request,session);assertEquals(400,response.getStatusCode().value());assertTrue(new String(response.getBody(),java.nio.charset.StandardCharsets.UTF_8).contains("INVALID_LOGO"));
 }
 @Test void oversizedProfileUploadIsRejectedBeforeForwarding()throws Exception{
  var core=mock(RestTemplate.class);var controller=new TokenController(mock(TokenService.class),core);ReflectionTestUtils.setField(controller,"coreUrl","http://example.invalid");var request=new MockHttpServletRequest("PUT","/api/business-profile");request.setContent(new byte[3*1024*1024+1]);var session=new MockHttpSession();session.setAttribute("ACCESS_TOKEN","unit-test-placeholder");session.setAttribute("LAST_PRESENCE_TOUCH",System.currentTimeMillis());assertEquals(413,controller.proxyRequest(request,session).getStatusCode().value());verify(core,never()).exchange(any(URI.class),eq(HttpMethod.PUT),any(),eq(byte[].class));
 }
}
