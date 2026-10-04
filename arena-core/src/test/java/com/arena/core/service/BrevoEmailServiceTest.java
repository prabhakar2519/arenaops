package com.arena.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.arena.core.entity.CustomerEntity;
import com.arena.core.entity.CustomerOnboardingCodeEntity;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class BrevoEmailServiceTest {

  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final BrevoEmailService emailService = new BrevoEmailService(mailSender);

  @BeforeEach
  void configureService() {
    ReflectionTestUtils.setField(emailService, "from", "noreply@arenaops.in");
    ReflectionTestUtils.setField(emailService, "fromName", "ArenaOps");
    ReflectionTestUtils.setField(emailService, "replyTo", "");
    ReflectionTestUtils.setField(emailService, "frontendUrl", "https://arenaops.in");
    when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
  }

  @Test
  void sendsCustomerInvitationWithRecipientAndActivationDetails() throws Exception {
    CustomerEntity customer = CustomerEntity.builder()
        .ownerName("Prabhu")
        .ownerEmail("owner@example.com")
        .parlourName("Cue Arena")
        .build();
    CustomerOnboardingCodeEntity code = CustomerOnboardingCodeEntity.builder()
        .code("ARENA-ABCD-2345")
        .expiresAt(LocalDateTime.of(2026, 9, 10, 18, 30))
        .build();

    emailService.sendInvitation(customer.getParlourName(), customer.getOwnerEmail(), code.getCode(), code.getExpiresAt());

    MimeMessage message = sentMessage();
    assertEquals("You're invited to ArenaOps", message.getSubject());
    assertEquals("owner@example.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
    String content = messageContent(message);
    assertTrue(content.contains("Cue Arena"));
    assertTrue(content.contains("ARENA-ABCD-2345"));
    assertTrue(content.contains("2026-09-10T18:30"));
    assertTrue(content.contains("can only be used once"));
    assertTrue(content.contains("https://arenaops.in/register#activationCode=ARENA-ABCD-2345&email=owner%40example.com"));
    assertTrue(content.contains("choose your username and password"));
  }

  @Test
  void sendsEmailVerificationThroughTheSameMailTransport() throws Exception {
    assertTrue(emailService.sendEmailVerification(
        "verify@example.com", "Venue Owner", "https://arenaops.in/verify-email?token=test-token"));

    MimeMessage message = sentMessage();
    assertEquals("Verify your ArenaOps email", message.getSubject());
    assertEquals("verify@example.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
    String content = messageContent(message);
    assertTrue(content.contains("Venue Owner"));
    assertTrue(content.contains("https://arenaops.in/verify-email?token=test-token"));
  }

  @Test
  void logsNestedAuthenticationFailureWithoutSecrets(org.springframework.boot.test.system.CapturedOutput output) {
    ReflectionTestUtils.setField(emailService, "smtpPassword", "test-secret-password");
    ReflectionTestUtils.setField(emailService, "smtpUsername", "test-smtp-login");
    var cause = new jakarta.mail.AuthenticationFailedException(
        "535 Authentication rejected test-secret-password test-smtp-login owner@example.com ARENA-ABCD-2345");
    var failure = new org.springframework.mail.MailAuthenticationException("Authentication failed", cause);
    org.mockito.Mockito.doThrow(failure).when(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

    var error = org.junit.jupiter.api.Assertions.assertThrows(com.arena.core.exception.ArenaOpsException.class,
        () -> emailService.sendInvitation("Venue", "owner@example.com", "ARENA-ABCD-2345", LocalDateTime.now()));
    assertEquals(com.arena.core.exception.ErrorCode.EMAIL_DELIVERY_FAILED, error.getErrorCode());

    String logs = output.getAll();
    assertTrue(logs.contains("preparing message"));
    assertTrue(logs.contains("submitting message to SMTP"));
    assertTrue(logs.contains("AuthenticationFailedException"));
    for (String secret : new String[] { "test-secret-password", "test-smtp-login", "owner@example.com", "ARENA-ABCD-2345" }) {
      org.junit.jupiter.api.Assertions.assertFalse(logs.contains(secret));
    }
    org.junit.jupiter.api.Assertions.assertFalse(logs.contains("SMTP accepted message"));
  }

  private MimeMessage sentMessage() throws Exception {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage message = captor.getValue();
    message.saveChanges();
    return message;
  }

  private String messageContent(MimeMessage message) throws Exception {
    return contentText(message.getContent());
  }

  private String contentText(Object content) throws Exception {
    if (!(content instanceof Multipart multipart)) {
      return content.toString();
    }
    StringBuilder text = new StringBuilder();
    for (int index = 0; index < multipart.getCount(); index++) {
      text.append(contentText(multipart.getBodyPart(index).getContent()));
    }
    return text.toString();
  }
}
