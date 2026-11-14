package com.arena.core.training;
/** Reusable notification boundary. Keycloak owns action token signing and Brevo SMTP delivery. */
public interface AccountSetupNotification { void send(String identityId,int lifespanSeconds); }
