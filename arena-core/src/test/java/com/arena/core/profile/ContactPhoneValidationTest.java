package com.arena.core.profile;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactPhoneValidationTest {
 private ProfileUpdate profile(String phone){return new ProfileUpdate(1L,"QA Club",null,null,phone,null,null,null,null,null,"KEEP");}
 @Test void optionalAndValidPhonesPass(){try(var factory=Validation.buildDefaultValidatorFactory()){for(String phone:new String[]{null,"","9876543210","98765 43210","+919876543210","+91 (98765) 43210","+442079460123"})assertTrue(factory.getValidator().validate(profile(phone)).isEmpty(),String.valueOf(phone));}}
 @Test void invalidPhoneIsReportedOnCorrectFormField(){try(var factory=Validation.buildDefaultValidatorFactory()){for(String phone:new String[]{"987654321","98765432100","1234567890","+91987654321","+911234567890","letters","---","+91","++919876543210"}){var errors=factory.getValidator().validate(profile(phone));assertTrue(errors.stream().anyMatch(e->e.getPropertyPath().toString().equals("publicPhone")),phone);}}}
 @Test void multipartApiRejectsInvalidPhoneBeforeCallingSave()throws Exception{var service=org.mockito.Mockito.mock(BusinessProfileService.class);var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new BusinessProfileController(service)).setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).setControllerAdvice(new com.arena.core.exception.GlobalExceptionHandler()).build();var json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(profile("987654321"));var part=new org.springframework.mock.web.MockMultipartFile("profile","profile.json","application/json",json);mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(org.springframework.http.HttpMethod.PUT,"/api/business-profile").file(part)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errorCode").value("VALIDATION_FAILED")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fieldErrors.publicPhone").exists());org.mockito.Mockito.verifyNoInteractions(service);}
}
