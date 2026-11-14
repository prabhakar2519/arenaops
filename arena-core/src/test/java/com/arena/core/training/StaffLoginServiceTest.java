package com.arena.core.training;
import com.arena.core.entity.*;
import com.arena.core.exception.*;
import com.arena.core.repository.*;
import com.arena.core.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StaffLoginServiceTest {
 AcademyAuthorization auth=mock(AcademyAuthorization.class);CustomerRepository customers=mock(CustomerRepository.class);AcademyStaffRepository staff=mock(AcademyStaffRepository.class);AppUserRepository users=mock(AppUserRepository.class);KeycloakService identities=mock(KeycloakService.class);AccountSetupNotification mail=mock(AccountSetupNotification.class);AuditService audit=mock(AuditService.class);
 StaffLoginService service=new StaffLoginService(auth,customers,staff,users,identities,mail,audit,mock(TrainingBatchRepository.class));
 Jwt owner=Jwt.withTokenValue("owner").header("alg","none").subject("owner").build();AcademyStaff m;CustomerEntity academy;
 @BeforeEach void setup(){academy=CustomerEntity.builder().id(10L).customerName("Academy").build();when(customers.findById(10L)).thenReturn(Optional.of(academy));when(customers.findBillingCustomerForUpdate(10L)).thenReturn(Optional.of(academy));when(auth.operations(owner)).thenReturn(new AcademyAuthorization.Actor(AppUserEntity.builder().customerId(10L).build(),null,Set.of("OWNER")));when(auth.owner(owner)).thenReturn(new AcademyAuthorization.Actor(AppUserEntity.builder().customerId(10L).build(),null,Set.of("OWNER")));m=new AcademyStaff();m.setId(20L);m.setAcademyId(10L);m.setName("Coach");m.setRole("COACH");m.setActive(true);m.setVersion(0L);when(staff.findById(20L)).thenReturn(Optional.of(m));when(staff.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));when(users.saveAndFlush(any())).thenAnswer(i->{AppUserEntity u=i.getArgument(0);u.setId(30L);return u;});when(identities.provisionAcademyStaff(any(),any(),any(),eq(10L),eq(20L),any())).thenReturn("kc-subject");}
 private StaffLoginService.Login request(){return new StaffLoginService.Login(0L,"coach","coach@example.test",Set.of("MANAGER","INSTRUCTOR"));}
 @Test void provisionsPendingAccountWithMultipleRolesAndNoPassword(){service.enable(owner,20L,request());assertEquals("PENDING",m.getLoginState());assertEquals("SENT",m.getNotificationState());assertEquals(Set.of("MANAGER","INSTRUCTOR"),m.getApplicationRoles());assertNotNull(m.getInvitationExpiresAt());var captor=org.mockito.ArgumentCaptor.forClass(AppUserEntity.class);verify(users).saveAndFlush(captor.capture());assertFalse(captor.getValue().getIsActive());assertEquals("!KEYCLOAK_MANAGED",captor.getValue().getPasswordHash());verify(mail).send("kc-subject",86400);assertThrows(ArenaOpsException.class,()->service.enable(owner,20L,request()));verify(identities,times(1)).provisionAcademyStaff(any(),any(),any(),any(),any(),any());}
 @Test void failedDeliveryPreservesAccountAndRetryDoesNotProvisionAgain(){doThrow(new ArenaOpsException(ErrorCode.EMAIL_DELIVERY_FAILED)).when(mail).send(any(),anyInt());assertThrows(ArenaOpsException.class,()->service.enable(owner,20L,request()));assertEquals("PENDING",m.getLoginState());assertEquals("FAILED",m.getNotificationState());assertEquals(30L,m.getAppUserId());doNothing().when(mail).send(any(),anyInt());service.resend(owner,20L,new StaffLoginService.Version(0L));assertEquals("SENT",m.getNotificationState());verify(identities,times(1)).provisionAcademyStaff(any(),any(),any(),any(),any(),any());assertThrows(ArenaOpsException.class,()->service.resend(owner,20L,new StaffLoginService.Version(0L)));m.setInvitationExpiresAt(LocalDateTime.now().minusSeconds(1));assertEquals("INVITATION_EXPIRED",m.getLoginAccessStatus());service.resend(owner,20L,new StaffLoginService.Version(0L));verify(mail,times(3)).send(any(),anyInt());}
 @Test void rolesAndCrossTenantRequestsAreRejectedBeforeProvision(){assertThrows(ArenaOpsException.class,()->service.enable(owner,20L,new StaffLoginService.Login(0L,"coach","coach@example.test",Set.of("OWNER"))));m.setAcademyId(99L);assertEquals(ErrorCode.RESOURCE_NOT_FOUND,assertThrows(ArenaOpsException.class,()->service.enable(owner,20L,request())).getErrorCode());verifyNoInteractions(identities,mail);}
 @Test void activationRequiresSubjectEmailAndCompletedRequiredActions(){service.enable(owner,20L,request());AppUserEntity user=AppUserEntity.builder().id(30L).customerId(10L).role("STAFF").isActive(false).build();when(staff.findByAppUserId(30L)).thenReturn(Optional.of(m));Jwt jwt=Jwt.withTokenValue("staff").header("alg","none").subject("kc-subject").claim("email_verified",true).build();assertThrows(ArenaOpsException.class,()->service.reconcile(jwt,user));assertFalse(user.getIsActive());when(identities.academyStaffSetupComplete("kc-subject")).thenReturn(true);service.reconcile(jwt,user);assertEquals("ACTIVE",m.getLoginState());assertTrue(user.getIsActive());Jwt foreign=Jwt.withTokenValue("other").header("alg","none").subject("other").build();assertThrows(ArenaOpsException.class,()->service.reconcile(foreign,user));}
 @Test void disablingRetainsStaffAndDeniesLocallyEvenIfKeycloakFails(){service.enable(owner,20L,request());AppUserEntity user=AppUserEntity.builder().id(30L).customerId(10L).role("STAFF").isActive(true).build();when(users.findById(30L)).thenReturn(Optional.of(user));when(staff.findByAppUserId(30L)).thenReturn(Optional.of(m));doThrow(new ArenaOpsException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)).when(identities).setAcademyIdentityEnabled("kc-subject",false);assertThrows(ArenaOpsException.class,()->service.disable(owner,20L,new StaffLoginService.Version(0L)));assertEquals("DISABLED",m.getLoginState());assertFalse(user.getIsActive());assertEquals(20L,m.getId());assertTrue(m.isActive());assertThrows(ArenaOpsException.class,()->service.reconcile(Jwt.withTokenValue("t").header("alg","none").subject("kc-subject").build(),user));verify(staff,never()).delete(any(AcademyStaff.class));}
 @Test void managersCannotProvisionOrChangeSecurityRoles(){Jwt manager=Jwt.withTokenValue("manager").header("alg","none").subject("manager").build();when(auth.operations(manager)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));when(auth.owner(manager)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));assertThrows(ArenaOpsException.class,()->service.enable(manager,20L,request()));assertThrows(ArenaOpsException.class,()->service.roles(manager,20L,Set.of("MANAGER"),0L));verifyNoInteractions(identities,mail);}
 @Test void ownerDelegationIsVersionedTenantScopedAndAudited(){m.setApplicationRoles(Set.of("MANAGER"));service.managerAccess(owner,20L,"FULL_OPERATIONS",0L);assertEquals("FULL_OPERATIONS",m.getManagerAccess());verify(audit).record(eq("ACADEMY_MANAGER_ACCESS_CHANGED"),eq(10L),any(),any(),eq("STANDARD"),eq("FULL_OPERATIONS"),isNull(),eq("staffId=20"));service.managerAccess(owner,20L,"STANDARD",0L);assertEquals("STANDARD",m.getManagerAccess());assertThrows(ArenaOpsException.class,()->service.managerAccess(owner,20L,"FULL_OPERATIONS",9L));m.setAcademyId(99L);assertEquals(ErrorCode.RESOURCE_NOT_FOUND,assertThrows(ArenaOpsException.class,()->service.managerAccess(owner,20L,"FULL_OPERATIONS",0L)).getErrorCode());}
 @Test void fullManagersCannotDelegateOrPromoteStaff(){Jwt full=Jwt.withTokenValue("test").header("alg","none").subject("manager").build();var membership=new AcademyStaff();membership.setManagerAccess("FULL_OPERATIONS");var actor=new AcademyAuthorization.Actor(AppUserEntity.builder().customerId(10L).build(),membership,Set.of("MANAGER"));when(auth.operations(full)).thenReturn(actor);when(auth.owner(full)).thenThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED));doThrow(new ArenaOpsException(ErrorCode.ACCESS_DENIED)).when(auth).personnel(eq(actor),eq(m),isNull(),eq(Set.of("MANAGER","INSTRUCTOR")));assertThrows(ArenaOpsException.class,()->service.enable(full,20L,request()));assertThrows(ArenaOpsException.class,()->service.managerAccess(full,20L,"FULL_OPERATIONS",0L));verifyNoInteractions(identities,mail);}
 @Test void reenableRejectsTokensIssuedBeforeDisableAndRequiresFreshVerifiedLogin(){
  service.enable(owner,20L,request());var user=AppUserEntity.builder().id(30L).customerId(10L).role("STAFF").isActive(true).build();
  when(users.findById(30L)).thenReturn(Optional.of(user));when(staff.findByAppUserId(30L)).thenReturn(Optional.of(m));
  var old=Jwt.withTokenValue("old").header("alg","none").subject("kc-subject").issuedAt(Instant.now().minusSeconds(30)).claim("email_verified",true).build();
  service.disable(owner,20L,new StaffLoginService.Version(0L));service.resume(owner,20L,new StaffLoginService.Version(0L));
  assertFalse(user.getIsActive());assertEquals("PENDING",m.getLoginState());assertThrows(ArenaOpsException.class,()->service.reconcile(old,user));
  when(identities.academyStaffSetupComplete("kc-subject")).thenReturn(true);
  var fresh=Jwt.withTokenValue("fresh").header("alg","none").subject("kc-subject").issuedAt(m.getAccessValidAfter().atZone(ZoneId.systemDefault()).toInstant().plusSeconds(1)).claim("email_verified",true).build();
  service.reconcile(fresh,user);assertTrue(user.getIsActive());assertEquals("ACTIVE",m.getLoginState());
 }
 @Test void legacyIdentityRequiresVerifiedEmailSubjectAndSameAcademyBeforeAdoption(){
  m.setLoginState("LEGACY_LINKED");m.setAppUserId(30L);var user=AppUserEntity.builder().id(30L).username("legacy").customerId(10L).role("COACH").isActive(true).build();
  when(staff.findByAppUserId(30L)).thenReturn(Optional.of(m));when(identities.getKeycloakUserId("legacy")).thenReturn("legacy-subject");when(identities.academyStaffSetupComplete("legacy-subject")).thenReturn(true);
  var verified=Jwt.withTokenValue("legacy").header("alg","none").subject("legacy-subject").claim("email_verified",true).build();
  user.setCustomerId(99L);assertThrows(ArenaOpsException.class,()->service.reconcile(verified,user));assertNull(m.getKeycloakSubject());
  user.setCustomerId(10L);service.reconcile(verified,user);assertEquals("legacy-subject",user.getKeycloakSubject());assertEquals(Set.of("INSTRUCTOR"),m.getApplicationRoles());assertEquals("ACTIVE",m.getLoginState());
 }
 @Test void legacyClubStaffKeepsExistingAccessButUnlinkedAcademyIdentityIsDenied(){
  var user=AppUserEntity.builder().id(90L).customerId(10L).role("STAFF").isActive(true).build();when(staff.findByAppUserId(90L)).thenReturn(Optional.empty());
  academy.setOrganizationType("CLUB");academy.setSports("SNOOKER");service.reconcile(owner,user);verifyNoInteractions(identities);
  academy.setOrganizationType("ACADEMY");assertThrows(ArenaOpsException.class,()->service.reconcile(owner,user));
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"MANAGER","STAFF"})
 void firstVerifiedLoginActivatesMembershipWithoutRealmRole(String role){
  service.enable(owner,20L,new StaffLoginService.Login(0L,"coach","coach@example.test",Set.of(role)));
  var user=AppUserEntity.builder().id(30L).username("coach").customerId(10L).keycloakSubject("kc-subject").role("STAFF").isActive(false).build();
  when(staff.findByAppUserId(30L)).thenReturn(Optional.of(m));
  when(identities.academyStaffSetupComplete("kc-subject")).thenReturn(true);
  var withoutSubject=Jwt.withTokenValue("missing").header("alg","none").claim("preferred_username","coach").claim("email_verified",true).build();
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(ArenaOpsException.class,()->service.reconcile(withoutSubject,user)).getErrorCode());
  assertFalse(user.getIsActive());assertEquals("PENDING",m.getLoginState());
  var verified=Jwt.withTokenValue("verified").header("alg","none").subject("kc-subject").claim("preferred_username","coach").claim("email_verified",true).build();
  service.reconcile(verified,user);
  var authorization=new AdminAuthorizationService();
  when(users.findByUsernameIgnoreCase("coach")).thenReturn(Optional.of(user));
  var actor=new AcademyAuthorization(users,customers,staff,mock(CustomerAccessService.class),authorization).actor(verified);
  assertTrue(user.getIsActive());assertEquals("ACTIVE",m.getLoginState());assertEquals(Set.of(role),actor.roles());
 }

 @Test void incompleteRegistrationRolesCannotBeChangedBehindItsImmutableRetryPayload(){m.setRegistrationRequestId(java.util.UUID.randomUUID().toString());m.setLoginState("PROVISIONING_FAILED");m.setActive(false);var before=Set.copyOf(m.getApplicationRoles());assertEquals(ErrorCode.INVALID_STATE,assertThrows(ArenaOpsException.class,()->service.roles(owner,20L,Set.of("MANAGER"),0L)).getErrorCode());assertEquals(before,m.getApplicationRoles());verifyNoInteractions(identities,mail);}
}
