package com.arena.core.training;
import com.arena.core.service.KeycloakService;
import com.arena.core.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="ARENA_TEST_KEYCLOAK_URL",matches=".+")
class AcademyKeycloakIntegrationTest {
 @Test void realProviderProvisionsNoPasswordAndSendsBrandedRequiredActionsEmail(){
  try(var client=KeycloakBuilder.builder().serverUrl(System.getenv("ARENA_TEST_KEYCLOAK_URL")).realm("master").clientId("admin-cli").username("validation").password("validation").build()){
   var service=new KeycloakService();ReflectionTestUtils.setField(service,"keycloak",client);ReflectionTestUtils.setField(service,"realm","arena-mvp-validation");ReflectionTestUtils.setField(service,"frontendUrl", "http://localhost:4200");
   String id=service.provisionAcademyStaff("coach.validation","coach@example.test","Coach",10L,20L,"Validation Academy");
   assertEquals(id,service.provisionAcademyStaff("coach.validation","coach@example.test","Coach",10L,20L,"Validation Academy"));
   assertEquals(ErrorCode.DUPLICATE_USERNAME,assertThrows(ArenaOpsException.class,()->service.provisionAcademyStaff("coach.validation","coach@example.test","Coach",99L,20L,"Other Academy")).getErrorCode());
   var user=client.realm("arena-mvp-validation").users().get(id);assertTrue(user.credentials().isEmpty());assertFalse(service.academyStaffSetupComplete(id));assertFalse(user.toRepresentation().isEmailVerified());var flows=client.realm("arena-mvp-validation").flows();var action=flows.getRequiredAction("ARENA_STAFF_SETUP");
   action.setEnabled(false);flows.updateRequiredAction("ARENA_STAFF_SETUP",action);
   assertEquals(ErrorCode.EMAIL_DELIVERY_FAILED,assertThrows(ArenaOpsException.class,()->service.sendAcademySetupEmail(id,86400)).getErrorCode());
   action.setEnabled(true);flows.updateRequiredAction("ARENA_STAFF_SETUP",action);service.sendAcademySetupEmail(id,86400);
  }
 }
}
