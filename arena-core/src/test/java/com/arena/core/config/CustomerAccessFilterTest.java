package com.arena.core.config;

import com.arena.core.entity.AppUserEntity;
import com.arena.core.exception.*;
import com.arena.core.repository.AppUserRepository;
import com.arena.core.service.AdminAuthorizationService;
import com.arena.core.service.CustomerAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerAccessFilterTest {
  @Test
  void entitlementFailuresUseStructured403InsteadOfServletHtml() throws Exception {
    var users = mock(AppUserRepository.class);
    var access = mock(CustomerAccessService.class);
    var authorization = mock(AdminAuthorizationService.class);
    var jwt = Jwt.withTokenValue("test").header("alg", "none").claim("sub", "owner").build();
    when(authorization.resolveUsername(jwt)).thenReturn("owner");
    var user = AppUserEntity.builder().username("owner").customerId(1L).build();
    when(users.findByUsername("owner")).thenReturn(Optional.of(user));
    doThrow(new ArenaOpsException(ErrorCode.CUSTOMER_ACCESS_BLOCKED)).when(access).assertCustomerCanUseApp(user);
    var filter = new CustomerAccessFilter(users, access, authorization, new ApiErrors(new ObjectMapper()));
    var response = new MockHttpServletResponse();
    try {
      SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
      filter.doFilter(new MockHttpServletRequest("GET", "/api/parlours"), response,
          (request, result) -> fail("A blocked customer must not reach the controller"));
      assertEquals(403, response.getStatus());
      assertEquals("CUSTOMER_ACCESS_BLOCKED", new ObjectMapper().readTree(response.getContentAsString()).path("errorCode").asText());
      assertTrue(response.getContentAsString().contains("correlationId"));
    } finally { SecurityContextHolder.clearContext(); }
  }
}
