package com.arena.core.training;

import com.arena.core.entity.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import static com.arena.core.training.TrainingRequests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffRegistrationTest {
 AppUserRepository users=mock(AppUserRepository.class);
 CustomerRepository customers=mock(CustomerRepository.class);
 CustomerAccessService access=mock(CustomerAccessService.class);
 AdminAuthorizationService authorization=mock(AdminAuthorizationService.class);
 AcademyStaffRepository staff=mock(AcademyStaffRepository.class);
 AcademySportRepository sports=mock(AcademySportRepository.class);
 TrainingBatchRepository batches=mock(TrainingBatchRepository.class);
 AuditService audit=mock(AuditService.class);
 AcademyAuthorization academyAuthorization=mock(AcademyAuthorization.class);
 TrainingService service=new TrainingService(users,customers,access,authorization,mock(SportCatalogueRepository.class),sports,staff,batches,mock(BatchScheduleRepository.class),audit,academyAuthorization,mock(AcademySetupService.class),mock(AcademyPlayAreaRepository.class));
 Jwt jwt=Jwt.withTokenValue("test").header("alg","none").subject("owner").claim("realm_access",Map.of("roles",List.of("OWNER"))).build();
 @BeforeEach void setup() {
  when(academyAuthorization.manage(jwt)).thenReturn(new AcademyAuthorization.Actor(AppUserEntity.builder().customerId(10L).build(),null,Set.of("OWNER")));
  when(authorization.resolveUsername(jwt)).thenReturn("owner");
  when(users.findByUsernameIgnoreCase("owner")).thenReturn(Optional.of(AppUserEntity.builder().id(1L).username("owner").role("OWNER").customerId(10L).isActive(true).build()));
  when(customers.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(CustomerEntity.builder().id(10L).organizationType("ACADEMY").build()));
  when(staff.saveAndFlush(any())).thenAnswer(i->{AcademyStaff s=i.getArgument(0);s.setId(20L);return s;});
 }
 @Test void controllerRegistersWithoutEmailOrAccountAndAudits() {
  var m=new TrainingController(service).staff(jwt,new Staff(null,"  Coach  ",null,"9876543210","COACH"));
  assertEquals("Coach",m.getName());assertEquals(10L,m.getAcademyId());assertNull(m.getAppUserId());assertNull(m.getEmail());assertEquals("NOT_ENABLED",m.getLoginAccessStatus());
  verify(users,never()).saveAndFlush(any());verify(users,never()).existsByNormalizedEmail(any());verify(audit).record(eq("TRAINING_STAFF_ADDED"),eq(10L),any(),eq("owner"),isNull(),isNull(),isNull(),eq("recordId=20"));
 }
 @Test void suppliedEmailAndLegacyUsernameNeverCreateOrLinkAccount() {
  var m=service.addStaff(jwt,new Staff("existing-owner","Coach","coach@example.test",null,"COACH"));
  assertNull(m.getAppUserId());assertEquals("coach@example.test",m.getEmail());verify(users,never()).findByUsernameIgnoreCase("existing-owner");verify(users,never()).saveAndFlush(any());
 }
 @Test void ordinaryStaffCanEditAndAssignSportsWithoutIdentity() {
  var m=service.addStaff(jwt,new Staff(null,"Coach",null,null,"COACH"));m.setVersion(0L);when(staff.findById(20L)).thenReturn(Optional.of(m));
  var updated=service.updateStaff(jwt,20L,new StaffUpdate(0L,"Coach updated","coach@example.test","1234567890","COACH",true));
  var s=new AcademySport();s.setId(30L);s.setAcademyId(10L);s.setActive(true);s.setTraining(true);when(sports.findById(30L)).thenReturn(Optional.of(s));
  service.assignSports(jwt,20L,new AssignSports(0L,Set.of(30L)));assertEquals(Set.of(30L),updated.getSportIds());assertNull(updated.getAppUserId());verify(users,never()).findById(any());
 }
 @Test void untrackedInvitationsAreBlockedAndForeignStaffIsHidden() {
  var m=new AcademyStaff();m.setAcademyId(10L);when(staff.findById(20L)).thenReturn(Optional.of(m));
  assertEquals(ErrorCode.STAFF_LOGIN_ACCESS_UNAVAILABLE,assertThrows(ArenaOpsException.class,()->service.invite(jwt,20L)).getErrorCode());
  m.setAcademyId(99L);assertEquals(ErrorCode.RESOURCE_NOT_FOUND,assertThrows(ArenaOpsException.class,()->service.invite(jwt,20L)).getErrorCode());
  assertEquals(ErrorCode.RESOURCE_NOT_FOUND,assertThrows(ArenaOpsException.class,()->service.updateStaff(jwt,20L,new StaffUpdate(0L,"Coach",null,null,"COACH",true))).getErrorCode());
 }
 @Test void coachRealmRoleCannotRegisterStaff() {
  Jwt coach=Jwt.withTokenValue("test").header("alg","none").subject("coach").claim("realm_access",Map.of("roles",List.of("COACH"))).build();
  when(academyAuthorization.manage(coach)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(ArenaOpsException.class,()->service.addStaff(coach,new Staff(null,"Coach",null,null,"COACH"))).getErrorCode());verify(staff,never()).saveAndFlush(any());
 }
 @Test void personnelChangesDoNotChangeLegacyIdentityLink() {
  var m=new AcademyStaff();m.setAcademyId(10L);m.setAppUserId(99L);m.setEmail("legacy@example.test");m.setRole("COACH");m.setVersion(0L);when(staff.findById(20L)).thenReturn(Optional.of(m));
  service.updateStaff(jwt,20L,new StaffUpdate(0L,"Staff",m.getEmail(),null,"STAFF",false));assertEquals(99L,m.getAppUserId());assertEquals("NOT_ENABLED",m.getLoginAccessStatus());verify(users,never()).findById(any());
 }
 @Test void optionalEmailValidationStillRejectsMalformedAddresses() {
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
   var validator=factory.getValidator();assertTrue(validator.validate(new Staff(null,"Coach",null,null,"COACH")).isEmpty());assertTrue(validator.validate(new Staff(null,"Coach","",null,"COACH")).isEmpty());assertFalse(validator.validate(new Staff(null,"Coach","not-email",null,"COACH")).isEmpty());
  }
 }
 @Test void singleSportClubCanRegisterEditAndAssignStaffWithoutChangingEntitlements(){var club=CustomerEntity.builder().id(10L).organizationType("CLUB").sports("BADMINTON").build();when(customers.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(club));when(customers.findById(10L)).thenReturn(Optional.of(club));var m=service.addStaff(jwt,new Staff(null,"Club Coach",null,null,"COACH"));m.setVersion(0L);when(staff.findById(20L)).thenReturn(Optional.of(m));assertEquals("Updated Coach",service.updateStaff(jwt,20L,new StaffUpdate(0L,"Updated Coach",null,null,"COACH",true)).getName());var s=new AcademySport();s.setId(30L);s.setAcademyId(10L);s.setActive(true);s.setTraining(true);when(sports.findById(30L)).thenReturn(Optional.of(s));assertEquals(Set.of(30L),service.assignSports(jwt,20L,new AssignSports(0L,Set.of(30L))).getSportIds());assertEquals("BADMINTON",club.getSports());assertEquals("CLUB",club.getOrganizationType());verify(audit).record(eq("TRAINING_STAFF_ADDED"),eq(10L),any(),any(),isNull(),isNull(),isNull(),any());}
 @Test void clubPersonnelWritesStillRequireOperationsPermission(){when(customers.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(CustomerEntity.builder().id(10L).organizationType("CLUB").build()));when(academyAuthorization.operations(jwt)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(ArenaOpsException.class,()->service.addStaff(jwt,new Staff(null,"Staff",null,null,"STAFF"))).getErrorCode());verify(staff,never()).saveAndFlush(any());}
}
