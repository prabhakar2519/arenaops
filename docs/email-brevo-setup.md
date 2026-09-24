# Local Brevo email

The backend sends invitations through Brevo SMTP when `app.email.enabled` is true.
The existing invitation creation and resend actions use this transport. SMTP errors
record an INVITATION_EMAIL_FAILED audit event; disabled delivery no longer reports success.

Local settings are in `arena-core/application-mail-local.yaml` (ignored by Git).
Start from the module directory so Spring imports that file:

```sh
cd arena-core
mvn spring-boot:run
```

Restart an existing backend after changing settings. The local file enables mail;
the source configuration defaults to disabled. Environment variables override the
SMTP defaults. Set `ARENAOPS_FRONTEND_URL` to the frontend URL recipients can reach.
The invitation links to `/register`, the activation route in this application.

An SMTP acceptance is not confirmation of inbox delivery. No live test email is
sent by the automated tests.
