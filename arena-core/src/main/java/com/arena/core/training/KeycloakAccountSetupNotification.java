package com.arena.core.training;
import com.arena.core.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class KeycloakAccountSetupNotification implements AccountSetupNotification {
 private final KeycloakService identities;
 public void send(String identityId,int lifespanSeconds){identities.sendAcademySetupEmail(identityId,lifespanSeconds);}
}
