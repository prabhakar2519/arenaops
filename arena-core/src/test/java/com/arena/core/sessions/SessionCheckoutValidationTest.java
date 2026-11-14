package com.arena.core.sessions;

import com.arena.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SessionCheckoutValidationTest {
 @Test void stopRejectsMissingQuoteAndNegativeMoneyBeforeCallingService() throws Exception {
  var service=mock(RunningSessionService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new RunningSessionController(service)).setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new GlobalExceptionHandler()).build();
  mvc.perform(post("/api/academy/running-sessions/1/stop").contentType(MediaType.APPLICATION_JSON).content("{\"paidAmount\":-1,\"discountAmount\":-2}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
   .andExpect(jsonPath("$.fieldErrors.quoteToken").exists()).andExpect(jsonPath("$.fieldErrors.paidAmount").exists());
  verifyNoInteractions(service);
 }
 @Test void decisionsRequireReasonBeforeCallingService() throws Exception {
  var service=mock(SessionCheckoutService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new SessionCheckoutController(service)).setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new GlobalExceptionHandler()).build();
  mvc.perform(post("/api/academy/running-sessions/discount-requests/1/decision").contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"reason\":\" \"}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
  verifyNoInteractions(service);
 }
}
