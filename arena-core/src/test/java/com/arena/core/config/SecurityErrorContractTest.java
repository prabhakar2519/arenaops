package com.arena.core.config;

import com.arena.core.exception.*;
import com.arena.core.repository.AppUserRepository;
import com.arena.core.service.AdminAuthorizationService;
import com.arena.core.service.CustomerAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityErrorContractTest {
  @Configuration
  @EnableWebMvc
  static class Beans {
    @Bean ApiErrors apiErrors() { return new ApiErrors(new ObjectMapper()); }
    @Bean CustomerAccessFilter accessFilter(ApiErrors errors) {
      return new CustomerAccessFilter(mock(AppUserRepository.class), mock(CustomerAccessService.class),
          mock(AdminAuthorizationService.class), errors);
    }
  }

  @Test
  void unauthenticatedRequestUsesStructured401FromActualSecurityChain() throws Exception {
    try (var context = new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
          java.util.Map.of("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "http://localhost/test-jwks")));
      context.register(Beans.class, SecurityConfig.class);
      context.refresh();
      var mvc = MockMvcBuilders.webAppContextSetup(context)
          .addFilters(new CorrelationIdFilter(), context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
      mvc.perform(get("/api/billing"))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/billing/orders/00000000-0000-0000-0000-000000000000/mock")
          .contentType("application/json").content("{}"))
          .andExpect(status().isUnauthorized());
      mvc.perform(get("/api/admin/customers"))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
          .andExpect(jsonPath("$.correlationId").exists()).andExpect(jsonPath("$.path").value("/api/admin/customers"));
    }
  }
}
