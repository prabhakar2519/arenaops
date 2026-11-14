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
    @Bean com.arena.core.checkin.FacilityCheckinController checkins(){return new com.arena.core.checkin.FacilityCheckinController(mock(com.arena.core.checkin.PermanentCheckinService.class),mock(com.arena.core.publicclub.PublicClubHost.class));}
    @Bean ApiErrors apiErrors() { return new ApiErrors(new ObjectMapper()); }
    @Bean CustomerAccessFilter accessFilter(ApiErrors errors) {
      return new CustomerAccessFilter(mock(AppUserRepository.class), mock(CustomerAccessService.class),
          mock(AdminAuthorizationService.class), errors,mock(com.arena.core.training.StaffLoginService.class));
    }
  }

  @Test
  void unauthenticatedRequestUsesStructured401FromActualSecurityChain() throws Exception {
    try (var context = new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
          java.util.Map.of("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", "http://localhost/test-jwks",
              "spring.security.oauth2.resourceserver.jwt.issuer-uri", "http://localhost/realms/arena-dev")));
      context.register(Beans.class, SecurityConfig.class);
      context.refresh();
      var mvc = MockMvcBuilders.webAppContextSetup(context)
          .addFilters(new CorrelationIdFilter(), context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/public/facility-check-ins/inspect").contentType("application/json").content("{\"token\":\""+"A".repeat(43)+"\"}"))
          .andExpect(status().isOk());
      mvc.perform(get("/api/public/facility-check-ins/inspect")).andExpect(status().isUnauthorized());
      for(String path:java.util.List.of("/api/academy/staff/login-availability","/api/academy/staff/registration","/api/academy/staff/1/registration/retry"))mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
      mvc.perform(get("/api/academy/facility-check-ins")).andExpect(status().isUnauthorized());
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/academy/facility-check-ins/1/confirm").contentType("application/json").content("{}"))
          .andExpect(status().isUnauthorized());
      mvc.perform(get("/api/academy/public-site/preview")).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/academy/public-site/preview/logo")).andExpect(status().isUnauthorized());
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
