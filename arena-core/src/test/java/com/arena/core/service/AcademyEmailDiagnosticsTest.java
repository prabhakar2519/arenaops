package com.arena.core.service;

import com.arena.core.exception.*;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RequiredActionProviderRepresentation;
import org.springframework.boot.test.system.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AcademyEmailDiagnosticsTest {
  Keycloak keycloak=mock(Keycloak.class);
  RealmResource realm=mock(RealmResource.class,RETURNS_DEEP_STUBS);
  KeycloakService service=new KeycloakService();
  @BeforeEach void setup(){ReflectionTestUtils.setField(service,"keycloak",keycloak);ReflectionTestUtils.setField(service,"realm","arena-dev");ReflectionTestUtils.setField(service,"frontendUrl","https://club.example.test");when(keycloak.realm("arena-dev")).thenReturn(realm);}
  private void action(boolean enabled){var action=new RequiredActionProviderRepresentation();action.setEnabled(enabled);action.setProviderId("ARENA_STAFF_SETUP");when(realm.flows().getRequiredAction("ARENA_STAFF_SETUP")).thenReturn(action);}
  @Test void setupEmailUsesApprovedClientLoginAndPreservesAllActions(){action(true);service.sendAcademySetupEmail("identity",86400);verify(realm.users().get("identity")).executeActionsEmail("arena-ui","https://club.example.test/login",86400,java.util.List.of("VERIFY_EMAIL","UPDATE_PASSWORD","ARENA_STAFF_SETUP"));}
  @Test void missingActionLogsExactStageAndNeverAttemptsEmail(CapturedOutput output){
    when(realm.flows().getRequiredAction("ARENA_STAFF_SETUP")).thenThrow(new NotFoundException());
    assertEquals(ErrorCode.EMAIL_DELIVERY_FAILED,assertThrows(ArenaOpsException.class,()->service.sendAcademySetupEmail("identity",86400)).getErrorCode());
    assertTrue(output.getAll().contains("reason=REQUIRED_ACTION_NOT_REGISTERED"));assertTrue(output.getAll().contains("stage=REQUIRED_ACTION_LOOKUP"));verify(realm,never()).users();
  }
  @Test void disabledActionLogsConfigurationReason(CapturedOutput output){
    action(false);assertThrows(ArenaOpsException.class,()->service.sendAcademySetupEmail("identity",86400));assertTrue(output.getAll().contains("reason=REQUIRED_ACTION_DISABLED"));verify(realm,never()).users();
  }
  @Test void providerFailureLogsSendStageWithoutLeakingProviderMessages(CapturedOutput output){
    action(true);var user=realm.users().get("identity");doThrow(new InternalServerErrorException("smtp-password-secret action-link-secret")).when(user).executeActionsEmail(anyString(),anyString(),anyInt(),anyList());
    assertThrows(ArenaOpsException.class,()->service.sendAcademySetupEmail("identity",86400));assertTrue(output.getAll().contains("stage=EXECUTE_ACTIONS_EMAIL"));assertTrue(output.getAll().contains("reason=KEYCLOAK_EMAIL_SEND_FAILED_CHECK_SERVER_SMTP_AND_THEME_LOGS"));assertFalse(output.getAll().contains("smtp-password-secret"));assertFalse(output.getAll().contains("action-link-secret"));
  }
}
