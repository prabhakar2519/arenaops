# SIT staff invitation delivery repair — 2026-11-15

## Cause

The staff resend endpoint returned `503 EMAIL_DELIVERY_FAILED`. SIT Keycloak was running its stock 26.5.3 image without the academy provider, so `ARENA_STAFF_SETUP` was unavailable. The academy email theme and realm SMTP configuration were also missing, and `arena-ui` did not allow the exact `https://sit.arenaops.in/login` return address. Core had working mail credentials, but the staff account-setup flow delegates delivery to Keycloak.

The existing staff membership was correctly retained in Invitation Pending. No staff record, login identity, role or permission was recreated or bypassed.

## Applied SIT repair

- Built the existing academy provider and tested it against disposable Keycloak 26.5.3.
- Installed the provider under `/opt/arenaops/sit/keycloak/providers/academy-keycloak-provider.jar` and copied only the academy email theme into the existing theme mount, preserving the login theme.
- Added a SIT-only, read-only provider mount and startup realm/theme configuration. Recreated only `sit-keycloak`, retaining its existing image, database, credentials, hostnames and loopback port.
- Confirmed the required action is enabled and is **not** a default action. Provider startup retains administrator-only academy identity attributes and the existing subject-scope safeguards.
- Configured realm SMTP using the existing SIT application credentials, held server-side; enabled STARTTLS. Verified TLS and SMTP authentication without issuing MAIL/RCPT/DATA or sending a message.
- Added only the exact setup `/login` return URL to `arena-ui`, preserving its login callbacks. The explicit configuration script now applies this safeguard on future setup runs.
- Core, BFF, UI and PostgreSQL container IDs were unchanged. PROD, DNS, TLS and Caddy were untouched.

The infrastructure fix is on `bugfix/sit-staff-invitation-provider` in the infrastructure repository. Merge that branch before the next infrastructure apply so its `common.sh` continues including the installed provider override. Ordinary application image deployments do not recreate Keycloak. Existing PostgreSQL realm data retains SMTP settings; rotate credentials there when the mail credentials change.

## Executed verification

- `mvn -B -f keycloak/academy-provider/pom.xml verify`: passed.
- `python3 -m unittest discover -s deploy/tests -v`: 23 passed, including exact redirect preservation, idempotency and unsafe-origin rejection.
- `mvn -B -f arena-core/pom.xml test -Dtest=StaffLoginServiceTest`: 14 passed.
- `deploy/validate-academy-keycloak.py` against disposable Keycloak plus captured local SMTP: passed (one Java integration test plus browser checks). Verified automatic provider registration/repair, academy attributes, subject scope restoration, branded email, password setup, verified email, single-use link replay rejection and expired-link rejection.
- Infrastructure provider tests: 4 passed; SIT opt-in, no provider fallback, DEV/PROD isolation, read-only mount and no embedded credentials.
- Live read-only diagnostics: Keycloak healthy; academy action registered/enabled/non-default; email theme available; SMTP present; exact setup redirect allowed. SMTP TLS/authentication succeeded without sending an email.

The first isolated test attempt exposed a stale fixture allowing only a login callback. Added the exact `/login` URL used by the real Java service and reran successfully. No production Java code, API contract, UI, billing or session logic changed.

## Remaining verification

No invitation was sent to the real staff member during this repair. The owner should click **Resend invitation** for the existing pending staff record. Actual recipient mailbox delivery and completion of that staff member's account setup remain unverified; successful SMTP authentication alone cannot prove inbox delivery. Do not recreate the member or remove the one-time required-action check to work around delivery problems.
