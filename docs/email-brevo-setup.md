# Local Brevo email

Invitation creation and resend use Brevo SMTP when `app.email.enabled` is true. SMTP errors record an INVITATION_EMAIL_FAILED audit event; disabled delivery does not report success.

Local SMTP credentials and the Keycloak client secret belong in the ignored repository-root `.env`. The `dev` profile loads it automatically using Spring's Java properties reader; do not surround values with shell quotes or prefix them with `export`.

Set these entries locally without putting real credentials in Git:

```properties
BREVO_SMTP_USERNAME=<local-smtp-login>
BREVO_SMTP_PASSWORD=<local-smtp-key>
KEYCLOAK_BFF_SECRET=<local-keycloak-client-secret>
ARENAOPS_MAIL_ENABLED=true
ARENAOPS_FRONTEND_URL=http://localhost:4200
```

From repository root:

```sh
cd arena-core
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Exported environment variables override file values. Restart core after editing `.env`. Set `ARENAOPS_FRONTEND_URL` to the address recipients can reach; invitation links use `/register`.

Production uses GitHub Environment runtime secrets and does not load this local file. An SMTP acceptance is not confirmation of inbox delivery; automated tests do not send a live email.
