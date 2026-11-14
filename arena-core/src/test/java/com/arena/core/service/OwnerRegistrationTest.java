package com.arena.core.service;

import com.arena.core.entity.*;
import com.arena.core.model.AppUserRequest;
import com.arena.core.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerRegistrationTest {
  @Test void licenseRegistrationCreatesActiveOwnerWithoutSetupPrerequisitesAndBindsSubject() {
    var users=mock(AppUserRepository.class);
    var passwords=mock(PasswordEncoder.class);
    var identities=mock(KeycloakService.class);
    var onboarding=mock(AdminCustomerService.class);
    var academy=CustomerEntity.builder().id(10L).organizationType("ACADEMY").setupStatus("INCOMPLETE").build();
    when(onboarding.activateCustomerFromInvitation("one-time-license","owner@example.test","owner"))
        .thenReturn(academy);
    when(passwords.encode("validation-password")).thenReturn("encoded-password");
    when(users.saveAndFlush(any())).thenAnswer(call->{AppUserEntity u=call.getArgument(0);u.setId(20L);return u;});
    when(identities.createUser("owner","validation-password","owner@example.test","OWNER")).thenReturn("owner-subject");
    var request=AppUserRequest.builder().username("owner").email("owner@example.test").password("validation-password")
        .name("Owner").role("OWNER").onboardingCode("one-time-license").build();
    var result=new AppUserService(users,passwords,identities,onboarding).save(request);
    assertTrue(result.getIsActive());assertEquals(10L,result.getCustomerId());assertEquals("OWNER",result.getRole());
    verify(onboarding).activateCustomerFromInvitation("one-time-license","owner@example.test","owner");
    var saved=org.mockito.ArgumentCaptor.forClass(AppUserEntity.class);verify(users,times(2)).saveAndFlush(saved.capture());
    assertEquals("owner-subject",saved.getValue().getKeycloakSubject());assertEquals("INCOMPLETE",academy.getSetupStatus());
  }
}
