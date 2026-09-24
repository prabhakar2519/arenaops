package com.arena.core.service;

import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@lombok.extern.slf4j.Slf4j
@Service
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingInvitationEmailService implements InvitationEmailService {
  @Override
  public void sendInvitation(String organizationName, String invitedEmail, String activationCode, LocalDateTime expiresAt) {
    log.warn("[Email] sending skipped: app.email.enabled=false; no SMTP request made");
    throw new IllegalStateException("Email delivery is disabled; configure app.email.enabled");
  }
}
