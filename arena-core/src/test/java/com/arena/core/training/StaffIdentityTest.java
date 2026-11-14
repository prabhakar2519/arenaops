package com.arena.core.training;
import com.arena.core.exception.*;
import com.arena.core.service.KeycloakService;
import org.junit.jupiter.api.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class StaffIdentityTest {
 KeycloakService service;RealmResource realm;UsersResource users;UserResource user;
 @BeforeEach void setup() {
  service=new KeycloakService();Keycloak keycloak=mock(Keycloak.class);realm=mock(RealmResource.class,RETURNS_DEEP_STUBS);users=mock(UsersResource.class);user=mock(UserResource.class,RETURNS_DEEP_STUBS);
  ReflectionTestUtils.setField(service,"keycloak",keycloak);ReflectionTestUtils.setField(service,"realm","arena-sit");when(keycloak.realm("arena-sit")).thenReturn(realm);when(realm.users()).thenReturn(users);when(users.get("id")).thenReturn(user);
  RoleRepresentation role=new RoleRepresentation();role.setName("COACH");when(realm.roles().get("COACH").toRepresentation()).thenReturn(role);
 }
 @Test void existingIdentityIsReusedWithoutPasswordOrProfileUpdate() {
  UserRepresentation existing=new UserRepresentation();existing.setId("id");existing.setUsername("coach");existing.setEmail("coach@example.test");when(users.search("coach",true)).thenReturn(List.of(existing));
  assertEquals("id",service.ensureStaffIdentity("coach","coach@example.test","Coach","COACH"));
  verify(users,never()).create(any());verify(user,never()).update(any());verify(user,never()).resetPassword(any());
  verify(user.roles().realmLevel()).add(anyList());
 }
 @Test void freshInvitationIdentityHasNoPasswordAndRequiresAccountActions() {
  when(users.search("coach",true)).thenReturn(List.of());when(users.create(any())).thenReturn(Response.created(URI.create("http://local/users/id")).build());
  assertEquals("id",service.ensureStaffIdentity("coach","coach@example.test","Coach","COACH"));
  var captured=org.mockito.ArgumentCaptor.forClass(UserRepresentation.class);verify(users).create(captured.capture());
  assertNull(captured.getValue().getCredentials());assertFalse(captured.getValue().isEmailVerified());assertEquals(List.of("UPDATE_PASSWORD","VERIFY_EMAIL"),captured.getValue().getRequiredActions());
 }
 @Test void mismatchedExistingEmailAndUnsupportedRolesAreRejected() {
  UserRepresentation existing=new UserRepresentation();existing.setUsername("coach");existing.setEmail("other@example.test");when(users.search("coach",true)).thenReturn(List.of(existing));
  assertEquals(ErrorCode.INVALID_REQUEST,assertThrows(ArenaOpsException.class,()->service.ensureStaffIdentity("coach","coach@example.test","Coach","COACH")).getErrorCode());
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(ArenaOpsException.class,()->service.ensureStaffIdentity("coach","coach@example.test","Coach","ADMIN")).getErrorCode());verify(users,never()).create(any());
 }
 @Test void invitationDeliveryFailureUsesGenericErrorWithoutProviderDetails() {
  UserRepresentation existing=new UserRepresentation();existing.setUsername("coach");existing.setId("id");when(users.search("coach",true)).thenReturn(List.of(existing));doThrow(new RuntimeException("provider secret body")).when(user).executeActionsEmail(anyList());
  assertEquals(ErrorCode.EMAIL_DELIVERY_FAILED,assertThrows(ArenaOpsException.class,()->service.inviteStaff("coach")).getErrorCode());
 }

 @Test void academyIdentifiersDetectRealmOnlyDuplicatesCaseInsensitively(){
  var existing=new UserRepresentation();existing.setId("external");existing.setUsername("COACH");existing.setEmail("used@example.test");when(users.search("coach",true)).thenReturn(List.of(existing));
  assertEquals(ErrorCode.DUPLICATE_USERNAME,assertThrows(ArenaOpsException.class,()->service.validateAcademyStaffIdentifiers("coach","new@example.test",10L,null)).getErrorCode());
  when(users.search("coach",true)).thenReturn(List.of());existing.setEmail("COACH@EXAMPLE.TEST");when(users.searchByEmail("coach@example.test",true)).thenReturn(List.of(existing));
  assertEquals(ErrorCode.DUPLICATE_EMAIL,assertThrows(ArenaOpsException.class,()->service.validateAcademyStaffIdentifiers("coach","coach@example.test",10L,null)).getErrorCode());verify(users,never()).create(any());verify(user,never()).remove();
 }
 @Test void academyRecoveryRequiresExactOwnershipAndEmailAndNeverMutatesExistingAccount(){
  var existing=new UserRepresentation();existing.setId("id");existing.setUsername("coach");existing.setEmail("coach@example.test");existing.setAttributes(java.util.Map.of("arenaAcademyId",List.of("10"),"arenaStaffId",List.of("20")));when(users.search("coach",true)).thenReturn(List.of(existing));when(users.searchByEmail("coach@example.test",true)).thenReturn(List.of(existing));when(user.toRepresentation()).thenReturn(existing);
  assertEquals("id",service.provisionAcademyStaff("coach","coach@example.test","Coach",10L,20L,"Club"));
  assertEquals(ErrorCode.DUPLICATE_USERNAME,assertThrows(ArenaOpsException.class,()->service.provisionAcademyStaff("coach","coach@example.test","Coach",11L,20L,"Other Club")).getErrorCode());verify(users,never()).create(any());verify(user,never()).update(any());verify(user,never()).remove();
 }
 @Test void academyCreateConflictIsRecheckedAndNoExistingAccountIsDeleted(){
  var external=new UserRepresentation();external.setUsername("coach");external.setEmail("other@example.test");when(users.search("coach",true)).thenReturn(List.of(),List.of(),List.of(external));when(users.create(any())).thenReturn(Response.status(409).build());
  assertEquals(ErrorCode.DUPLICATE_USERNAME,assertThrows(ArenaOpsException.class,()->service.provisionAcademyStaff("coach","coach@example.test","Coach",10L,20L,"Club")).getErrorCode());verify(user,never()).remove();
 }
}
