package com.arena.core.service;

import com.arena.core.exception.ArenaOpsException;
import com.arena.core.exception.ErrorCode;

import com.arena.core.entity.CustomerEntity;
import com.arena.core.entity.CustomerOnboardingCodeEntity;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class BrevoEmailService implements InvitationEmailService {

  private final JavaMailSender mailSender;

  @Value("${spring.mail.host}")
  private String smtpHost;
  @Value("${spring.mail.port}")
  private int smtpPort;
  @Value("${spring.mail.username}")
  private String smtpUsername;
  @Value("${spring.mail.password}")
  private String smtpPassword;

  @Value("${app.email.from}")
  private String from;

  @Value("${app.email.from-name}")
  private String fromName;

  @Value("${app.email.reply-to}")
  private String replyTo;

  @Value("${app.email.frontend-url}")
  private String frontendUrl;

  @Override
  public void sendInvitation(String organizationName, String invitedEmail, String activationCode, java.time.LocalDateTime expiresAt) {
    CustomerEntity customer = CustomerEntity.builder().ownerEmail(invitedEmail).parlourName(organizationName).build();
    CustomerOnboardingCodeEntity code = CustomerOnboardingCodeEntity.builder().code(activationCode).expiresAt(expiresAt).build();
    String activationUrl = UriComponentsBuilder.fromUriString(frontendUrl)
        .pathSegment("register")
        .fragment("activationCode={code}&email={email}")
        .encode()
        .buildAndExpand(activationCode, invitedEmail)
        .toUriString();
    String subject = "You're invited to ArenaOps";
    String plainText = invitationPlainText(customer, code, activationUrl);
    String htmlText = invitationHtmlText(customer, code, activationUrl);
    send(customer.getOwnerEmail(), subject, plainText, htmlText);
  }

  public boolean sendEmailVerification(
      String recipientEmail, String recipientName, String verificationUrl) {
    String safeName = displayName(recipientName);
    String subject = "Verify your ArenaOps email";
    String plainText = "Hello " + safeName + ",\n\n"
        + "Verify your email address to finish setting up your ArenaOps account:\n"
        + verificationUrl + "\n\n"
        + "If you did not request this, you can ignore this email.\n\nArenaOps";
    String htmlText = emailLayout(
        "Verify your email",
        "<p style=\"line-height:1.6\">Hello " + HtmlUtils.htmlEscape(safeName) + ",</p>"
            + "<p style=\"line-height:1.6\">Verify your email address to finish setting up your ArenaOps account.</p>"
            + button(verificationUrl, "Verify email")
            + "<p style=\"margin-top:26px;color:#53645b;font-size:13px;line-height:1.5\">"
            + "If you did not request this, you can ignore this email.</p>");
    send(recipientEmail, subject, plainText, htmlText);
    return true;
  }

  private void send(String recipient, String subject, String plainText, String htmlText) {
    long started = System.nanoTime();
    String attemptId = java.util.UUID.randomUUID().toString();
    log.info("[Email] attempt={} preparing message; transport=SMTP", attemptId);
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(from, fromName);
      helper.setTo(recipient);
      if (replyTo != null && !replyTo.isBlank()) {
        helper.setReplyTo(replyTo);
      }
      helper.setSubject(subject);
      helper.setText(plainText, htmlText);
      log.info("[Email] attempt={} submitting message to SMTP; connection, TLS and authentication handled by mail transport", attemptId);
      mailSender.send(message);
      log.info("[Email] attempt={} SMTP accepted message; elapsedMs={}; inbox delivery is not yet confirmed",
          attemptId, (System.nanoTime() - started) / 1_000_000);
    } catch (MessagingException | UnsupportedEncodingException exception) {
      logFailure(attemptId, "message preparation", started, exception, recipient);
      throw new ArenaOpsException(ErrorCode.EMAIL_DELIVERY_FAILED);
    } catch (RuntimeException exception) {
      logFailure(attemptId, "SMTP submission", started, exception, recipient);
      throw new ArenaOpsException(ErrorCode.EMAIL_DELIVERY_FAILED);
    }
  }

  private void logFailure(String attemptId, String stage, long started, Throwable exception, String recipient) {
    log.error("[Email] attempt={} failed at {}; elapsedMs={}", attemptId, stage,
        (System.nanoTime() - started) / 1_000_000);
    var pending = new java.util.ArrayDeque<Throwable>();
    var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
    pending.add(exception);
    while (!pending.isEmpty() && seen.size() < 20) {
      Throwable failure = pending.remove();
      if (!seen.add(failure)) continue;
      // Provider exception messages may contain SMTP credentials, tokens, and full message bodies.
      log.error("[Email] attempt={} cause={}", attemptId, failure.getClass().getSimpleName());
      if (failure.getCause() != null) pending.add(failure.getCause());
      if (failure instanceof MessagingException mail && mail.getNextException() != null) pending.add(mail.getNextException());
      if (failure instanceof org.springframework.mail.MailSendException send) {
        for (Exception nested : send.getMessageExceptions()) pending.add(nested);
      }
    }
  }

  private String invitationPlainText(
      CustomerEntity customer, CustomerOnboardingCodeEntity code, String activationUrl) {
    return "Hello " + displayName(customer.getOwnerName()) + ",\n\n"
        + "You've been invited to activate " + customer.getParlourName() + " on ArenaOps.\n\n"
        + "Activation code: " + code.getCode() + "\n"
        + "Valid until: " + code.getExpiresAt() + "\n"
        + "Activate ArenaOps: " + activationUrl + "\n\n"
        + "Open the activation link to choose your username and password. "
        + "The activation code can only be used once.\n\nArenaOps\n"
        + "Sports Academy & Venue Management";
  }

  private String invitationHtmlText(
      CustomerEntity customer, CustomerOnboardingCodeEntity code, String activationUrl) {
    String owner = HtmlUtils.htmlEscape(displayName(customer.getOwnerName()));
    String organization = HtmlUtils.htmlEscape(customer.getParlourName());
    String activationCode = HtmlUtils.htmlEscape(code.getCode());
    String expiry = HtmlUtils.htmlEscape(code.getExpiresAt().toString());
    return emailLayout(
        "You're invited",
        "<p style=\"line-height:1.6\">Hello " + owner + ",</p>"
            + "<p style=\"line-height:1.6\">You've been invited to activate <strong>"
            + organization + "</strong> on ArenaOps.</p>"
            + "<p style=\"margin:26px 0 8px;color:#53645b;font-size:13px;text-transform:uppercase\">Activation code</p>"
            + "<p style=\"margin:0 0 10px;font-size:24px;font-weight:700;letter-spacing:2px\">"
            + activationCode + "</p>"
            + "<p style=\"margin:0 0 26px;color:#53645b;font-size:13px\">Valid until " + expiry + "</p>"
            + button(activationUrl, "Activate ArenaOps")
            + "<p style=\"margin-top:26px;color:#53645b;font-size:13px;line-height:1.5\">"
            + "Open the activation link to choose your username and password. The code can only be used once, after successful registration.</p>");
  }

  private String emailLayout(String heading, String body) {
    return "<!doctype html><html><body style=\"margin:0;background:#f4f7f5;font-family:Arial,sans-serif;color:#17231d\">"
        + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"padding:32px 16px;background:#f4f7f5\">"
        + "<tr><td align=\"center\"><table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" "
        + "style=\"max-width:600px;background:#fff;border:1px solid #dbe5df\">"
        + "<tr><td style=\"padding:24px 32px;background:#102d20;color:#fff\">"
        + "<div style=\"font-size:24px;font-weight:700\">ArenaOps</div>"
        + "<div style=\"margin-top:4px;font-size:12px;color:#c9d8cf\">Sports Academy &amp; Venue Management</div></td></tr>"
        + "<tr><td style=\"padding:32px\"><h1 style=\"margin:0 0 18px;font-size:26px\">"
        + HtmlUtils.htmlEscape(heading) + "</h1>" + body + "</td></tr></table></td></tr></table></body></html>";
  }

  private String button(String url, String label) {
    return "<a href=\"" + HtmlUtils.htmlEscape(url)
        + "\" style=\"display:inline-block;padding:13px 22px;background:#168a4b;color:#fff;text-decoration:none;font-weight:700\">"
        + HtmlUtils.htmlEscape(label) + "</a>";
  }

  private String displayName(String name) {
    return name == null || name.isBlank() ? "there" : name.trim();
  }
}
