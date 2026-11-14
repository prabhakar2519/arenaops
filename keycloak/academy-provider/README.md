# Single-use academy account setup

Build with `mvn package`, then mount the JAR in `/opt/keycloak/providers` and restart/build Keycloak. Register and **enable** `ARENA_STAFF_SETUP` in the realm; it must not be a default action. `deploy/configure-academy-keycloak.py` performs this configuration explicitly.

The factory marks staff execute-actions tokens as one-time. The action itself immediately succeeds; email verification and password setup remain Keycloak's built-in actions. It collects no credentials, assigns no roles, and does not activate application membership. Core verifies completed setup and membership independently.

Keycloak checks enabled required-action factories when deciding whether an execute-actions token can be reused. See [Keycloak's handler implementation](https://github.com/keycloak/keycloak/blob/26.0.0/services/src/main/java/org/keycloak/authentication/actiontoken/execactions/ExecuteActionsActionTokenHandler.java). Revalidate this internal SPI and link replay when upgrading Keycloak.

## Automatic development startup

The infrastructure dev Compose override mounts the JAR and sets `ARENA_ACADEMY_REALM=arena-dev`. The provider registers/enables its action on Keycloak's post-migration startup event and on creation of that realm. It repairs disabled/default-action settings idempotently; the action is never a default for all users. No admin credentials, polling process or sidecar is needed for registration.

From the infrastructure repository run `python3 scripts/start-dev-keycloak.py`. This builds the provider, preserves the existing local PostgreSQL project/network, recreates only dev Keycloak, and waits for readiness. Existing local Brevo settings are read from the ignored infrastructure `.env` and applied on startup; the branded email theme is mounted with the existing login theme. Ordinary restarts/recreations using the dev Compose override retain the startup hook. SIT/production overrides do not install or enable it automatically.

`ARENA_ACADEMY_EMAIL_THEME` and `ARENA_ACADEMY_SMTP_*` configure the designated realm only. Without these SMTP variables, existing realm SMTP settings are preserved. Never log or commit SMTP passwords. Revalidate provider startup, required-action replay and expiry when upgrading Keycloak.

Startup also registers `arenaAcademyId`, `arenaStaffId`, and `arenaAcademyName` as single-value attributes visible and editable only by administrators. This preserves the academy metadata supplied by core provisioning. Setup emails use that academy name; they never fall back to the environment/realm name. Accounts created before this configuration may need their academy attributes repaired from their verified staff membership before resending.

For Keycloak 26, startup attaches the built-in `basic` default client scope to `arena-ui` and `arena-bff`. This supplies the access-token `sub` claim needed to bind authenticated staff to their stored identity; absence must never be worked around by trusting a username alone. Infrastructure realm templates and bootstrap also preserve this scope.
