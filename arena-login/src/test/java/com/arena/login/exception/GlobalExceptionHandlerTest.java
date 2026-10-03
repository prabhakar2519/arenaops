package com.arena.login.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController(), new ApiErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter()).build();
    }

    @RestController
    static class TestController {
        record Input(@NotBlank @Email String email) {}
        @PostMapping("/test/validation") public void validate(@Valid @RequestBody Input input) {}
        @GetMapping("/test/business") public void business() { throw new ArenaOpsException(ErrorCode.CUSTOMER_NOT_FOUND); }
        @GetMapping("/test/conflict") public void conflict() { throw new ArenaOpsException(ErrorCode.CUSTOMER_ALREADY_EXISTS); }
        @GetMapping("/test/unexpected") public void unexpected() { throw new RuntimeException("password=super-secret SQL select access_token"); }
        @GetMapping("/test/legacy") public void legacy() { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "sensitive provider body"); }
    }

    @Test void businessErrorsUseStableCodesStatusAndCorrelation() throws Exception {
        String id = "12345678-1234-1234-1234-123456789abc";
        mvc.perform(get("/test/business").header(CorrelationIdFilter.HEADER, id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/test/business"))
                .andExpect(jsonPath("$.correlationId").value(id))
                .andExpect(header().string(CorrelationIdFilter.HEADER, id))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get("/test/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_ALREADY_EXISTS"));
    }

    @Test void invalidFieldsAndMalformedBodiesAreSafeAndStructured() throws Exception {
        mvc.perform(post("/test/validation").contentType("application/json").content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.reasonCode").value("INVALID_REQUEST_FIELDS"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        mvc.perform(post("/test/validation").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test void unknownAndLegacyErrorsNeverExposeRawMessages() throws Exception {
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("super-secret"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(get("/test/legacy")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider body"))));
    }

    @Test void unsupportedMethodsAndMissingRoutesDoNotBecome500() throws Exception {
        mvc.perform(post("/test/business")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mvc.perform(get("/error")
                .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404)
                .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/api/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/missing"));
    }
}
