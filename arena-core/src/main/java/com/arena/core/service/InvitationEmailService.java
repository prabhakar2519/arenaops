package com.arena.core.service;

import java.time.LocalDateTime;

public interface InvitationEmailService {

  void sendInvitation(String organizationName, String invitedEmail, String activationCode, LocalDateTime expiresAt);
}
