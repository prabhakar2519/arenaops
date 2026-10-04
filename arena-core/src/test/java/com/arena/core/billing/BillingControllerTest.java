package com.arena.core.billing;

import com.arena.core.exception.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BillingControllerTest {
  BillingService service = mock(BillingService.class);
  Jwt jwt = Jwt.withTokenValue("test").header("alg","none").subject("owner").build();
  MockMvc mvc;
  @BeforeEach void setup() {
    mvc = MockMvcBuilders.standaloneSetup(new BillingController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
          public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == Jwt.class; }
          public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest w, WebDataBinderFactory b) { return jwt; }
        }).build();
  }
  @Test void rejectsNullInvalidCycleAndHiddenAmountOrTenantFields() throws Exception {
    for (String request : new String[]{"{}", "{\"planId\":\"STANDARD\",\"cycle\":\"WEEKLY\"}",
        "{\"planId\":\"STANDARD\",\"cycle\":\"MONTHLY\",\"requestId\":\"" + UUID.randomUUID() + "\",\"amount\":1}",
        "{\"planId\":\"STANDARD\",\"cycle\":\"MONTHLY\",\"requestId\":\"" + UUID.randomUUID() + "\",\"customerId\":999}"}) {
      mvc.perform(post("/api/billing/orders").contentType("application/json").content(request)).andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
  }
  @Test void rejectsMissingAttemptAndCredentialFields() throws Exception {
    String url = "/api/billing/orders/" + UUID.randomUUID() + "/mock";
    mvc.perform(post(url).contentType("application/json").content("{\"outcome\":\"PAID\"}")).andExpect(status().isBadRequest());
    mvc.perform(post(url).contentType("application/json").content("{\"attemptId\":\"" + UUID.randomUUID()
        + "\",\"outcome\":\"PAID\",\"cvv\":\"123\"}")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
  @Test void forwardsAuthenticatedIdentityAndSanitizesForbiddenResponse() throws Exception {
    when(service.overview(jwt)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));
    mvc.perform(get("/api/billing")).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    verify(service).overview(jwt);
  }
  @Test void validSelectionDelegatesWithServerIdentity() throws Exception {
    UUID key = UUID.randomUUID();
    mvc.perform(post("/api/billing/orders").contentType("application/json").content(
        "{\"planId\":\"STANDARD\",\"cycle\":\"ANNUAL\",\"requestId\":\"" + key + "\"}"))
        .andExpect(status().isOk());
    verify(service).create(jwt,new BillingTypes.CreateOrder("STANDARD",BillingTypes.Cycle.ANNUAL,key));
  }
  @Test void malformedOrderIdentifierRejected() throws Exception {
    mvc.perform(get("/api/billing/orders/invalid")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
