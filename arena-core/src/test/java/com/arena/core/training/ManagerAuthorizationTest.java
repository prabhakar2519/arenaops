package com.arena.core.training;
import com.arena.core.entity.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import com.arena.core.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ManagerAuthorizationTest {
 final AppUserRepository users=mock(AppUserRepository.class);
 final CustomerRepository customers=mock(CustomerRepository.class);
 final AcademyStaffRepository staff=mock(AcademyStaffRepository.class);
 final AcademyAuthorization auth=new AcademyAuthorization(users,customers,staff,mock(CustomerAccessService.class),new AdminAuthorizationService());
 final Jwt jwt=Jwt.withTokenValue("test").header("alg","none").subject("manager-subject").claim("preferred_username","manager").build();
 AcademyStaff manager;
 @BeforeEach void setup(){
  var user=AppUserEntity.builder().id(1L).customerId(10L).username("manager").role("STAFF").isActive(true).keycloakSubject("manager-subject").build();
  manager=new AcademyStaff();manager.setAcademyId(10L);manager.setActive(true);manager.setLoginState("ACTIVE");manager.setKeycloakSubject("manager-subject");manager.setApplicationRoles(new HashSet<>(Set.of("MANAGER")));
  when(users.findByUsernameIgnoreCase("manager")).thenReturn(Optional.of(user));when(staff.findByAppUserId(1L)).thenReturn(Optional.of(manager));when(customers.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(CustomerEntity.builder().id(10L).build()));
 }
 @Test void defaultManagerCanManageOperationsButCannotEditProfileOrPersonnel(){assertTrue(auth.manage(jwt).manager());assertFalse(auth.actor(jwt).operations());assertThrows(ArenaOpsException.class,()->auth.operations(jwt));}
 @Test void promotionAndDowngradeApplyToSameJwtOnNextRequest(){manager.setManagerAccess("FULL_OPERATIONS");assertTrue(auth.operations(jwt).operations());manager.setManagerAccess("STANDARD");assertThrows(ArenaOpsException.class,()->auth.operations(jwt));}
 @Test void inactiveAndForeignMembershipDenied(){manager.setActive(false);assertThrows(ArenaOpsException.class,()->auth.actor(jwt));manager.setActive(true);manager.setAcademyId(99L);assertThrows(ArenaOpsException.class,()->auth.actor(jwt));}
 @Test void fullManagerCannotManageOrPromoteManagersOrForeignStaff(){manager.setManagerAccess("FULL_OPERATIONS");var actor=auth.operations(jwt);var target=new AcademyStaff();target.setAcademyId(10L);auth.personnel(actor,target,"COACH",Set.of("STAFF"));assertThrows(ArenaOpsException.class,()->auth.personnel(actor,target,"MANAGER",Set.of("STAFF")));assertThrows(ArenaOpsException.class,()->auth.personnel(actor,target,null,Set.of("MANAGER")));target.setApplicationRoles(Set.of("MANAGER"));assertThrows(ArenaOpsException.class,()->auth.personnel(actor,target,null,null));target.setApplicationRoles(Set.of());target.setAcademyId(99L);assertThrows(ArenaOpsException.class,()->auth.personnel(actor,target,null,null));}
 @Test void linkedOwnersCannotBeManagedByFullManager(){manager.setManagerAccess("FULL_OPERATIONS");var target=new AcademyStaff();target.setAcademyId(10L);target.setAppUserId(2L);when(users.findById(2L)).thenReturn(Optional.of(AppUserEntity.builder().role("OWNER").build()));assertThrows(ArenaOpsException.class,()->auth.personnel(auth.operations(jwt),target,null,null));}
 @Test void ordinaryStaffCannotManageOrEdit(){manager.setApplicationRoles(Set.of("STAFF"));assertFalse(auth.actor(jwt).operations());assertThrows(ArenaOpsException.class,()->auth.manage(jwt));}
 @Test void coachesAndStaffCannotUseAdministrationOrPersonnelApis(){for(String role:Set.of("STAFF","INSTRUCTOR","COACH")){manager.setApplicationRoles(Set.of(role));assertThrows(ArenaOpsException.class,()->auth.manage(jwt));assertThrows(ArenaOpsException.class,()->auth.operations(jwt));assertThrows(ArenaOpsException.class,()->auth.owner(jwt));}}
 @Test void managerRoleRevocationAppliesToSameJwtOnNextRequest(){assertTrue(auth.manage(jwt).manager());manager.setApplicationRoles(Set.of("STAFF"));assertThrows(ArenaOpsException.class,()->auth.manage(jwt));}
}
