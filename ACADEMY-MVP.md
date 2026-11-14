# Academy Setup MVP v1.0

Review branch: `feature/academy-setup-mvp-v1`. The pasted MVP specification is the implementation baseline; Confluence was unavailable. [Initial gap analysis](MVP-PLAN.md).

## Implemented workflow

Existing owner registration, license consumption, trial and subscription enforcement are retained. Academy owners land on `/academy/dashboard` without an additional setup gate. The backend evaluates profile, active sports, staff or owner-managed confirmation, applicable batches/facilities, and owner review. Percentage counts applicable steps only. Losing a prerequisite clears review; restoring it requires a fresh review.

Each sport has its own Training, Facility or Both model. Existing catalogue, custom activity rules, batch/coach assignments, capacity, schedules, conflict checks and optimistic versions are reused. Owner-managed academies can schedule batches without additional coaches. Academy play areas use tenant/sport foreign keys and codes unique within the academy; existing legacy Snooker/Badminton venue operations remain separate.

Staff registration creates a business record without an identity or email by default. Designations are Manager, Coach, Receptionist and General Staff. An owner can explicitly enable login, assign multiple predefined roles, resend a failed/expired notification, disable login without deleting the employee, and re-enable it. Login provisioning failures and email failures remain visible and retryable. Pending accounts activate only after a verified Keycloak login and completed required actions. Identity subjects, academy membership and token issuance time are checked. Local disable takes effect even if the provider call fails.

## Authorization

| Role | Academy configuration | Identity and roles | Billing | Assignment reads |
| --- | --- | --- | --- | --- |
| OWNER | Full | Full, except creating a second owner | Full | All |
| MANAGER | Sports, business staff, batches, facilities | Denied | Denied | All |
| INSTRUCTOR | Denied | Denied | Denied | Assigned sports and batches |
| STAFF | Denied | Denied | Denied | Limited dashboard/context |
| PLATFORM_ADMIN / legacy ADMIN | Existing platform administration | Existing platform flow | Existing platform flow | No implicit academy membership |

Roles are independent; Manager does not imply Instructor. Existing student/attendance/report implementations are retained; this MVP adds no unrelated attendance or booking engine. Backend academy services enforce permissions and tenant scope, independently of navigation.

## Academy operations dashboard

`/academy/dashboard` uses the academy-scoped facility session API, not the legacy parlour/session endpoints. It presents active facilities by sport, occupancy and availability, live sessions, recent completed or unsettled sessions, and today's facility-session charges, collections and pending amounts by sport. Facility-session totals remain separate from student training fees and ArenaOps subscription billing. Empty academies return zero-valued summaries and empty session/collection lists.

Session timing excludes pauses. Snooker billing rounds down to whole currency units and Badminton retains two decimal places, matching the existing SPMS session rules. The ArenaOps dashboard does not yet include SPMS reservation workflows; those remain outside the academy facility-session model.

## Data and API changes

Liquibase additions under `R1_10_academy_training` extend existing customer, app_user and academy_staff records. The additions are membership_role and academy_play_area, rather than duplicate organization/user/staff tables. Partial unique indexes enforce one active owner per customer, unique identity subjects, and case-insensitive usernames/emails. Existing conflicting data stops migration for review; it is never silently deleted. Legacy staff identities are adopted only after verified subject, academy and email checks.

- `GET /api/academy/setup`, `PUT /setup/profile`, `PUT /setup/confirmation`
- `GET /api/academy/membership`
- `GET/POST /api/academy/facilities`, `PUT /facilities/{id}`
- `POST /api/academy/staff/{id}/login` and `/resend`, `/disable`, `/enable`
- `PUT /api/academy/staff/{id}/login/roles`

All mutation payloads carry the current version. The old implicit staff invitation endpoint remains blocked. Sensitive changes use existing audit records and standardized API errors. Keycloak alone stores credentials; app_user contains a non-password sentinel for managed identities.

## Local installation before restart

No SIT/production deployment or live database migration was performed. The running port 4200 process may still serve the older redesign worktree. Start this branch's UI from **this repository**, after applying the backend changes.

1. Back up/review local data and enable Liquibase for core startup. Apply the master changelog, including all three academy feature migrations. A core process started with Liquibase disabled will not install these tables/columns.
2. Build `mvn -f keycloak/academy-provider/pom.xml package`. Mount the resulting `academy-keycloak-provider-1.0.0.jar` into `/opt/keycloak/providers`, and mount `keycloak/themes` into `/opt/keycloak/themes`. Restart/build Keycloak so it discovers the provider. This required-action SPI was validated on Keycloak 26.5.3; revalidate when upgrading Keycloak.
3. Configure the local realm using `deploy/configure-academy-keycloak.py --url <admin-base-url> --realm <realm> --public-url <browser-visible-keycloak-base-url>`. It reads `KC_ADMIN_CLIENT_ID`, `KC_ADMIN_CLIENT_SECRET`, `BREVO_SMTP_USERNAME`, `BREVO_SMTP_PASSWORD`, `ARENAOPS_MAIL_FROM`, and optional `BREVO_SMTP_HOST`/`BREVO_SMTP_PORT` from the environment. It never prints them. The admin client needs realm/user management permissions. The script registers the single-use action, protects academy identity attributes from user edits, selects `arena-academy` email branding, enables password recovery, and sets Brevo SMTP with STARTTLS.
4. Ensure OIDC issuer, browser-visible Keycloak URL, UI callback URI and allowed web origins agree. For LAN access use the reachable LAN host rather than localhost, with HTTPS for browser secure-context APIs. Configure exact callback/origin entries for the intended local/LAN URLs; do not use unrestricted wildcards.
5. Restart core/login and run `npm --prefix arena-ui run start:dev` from this checkout. No secret development YAML contents were added to source control.

The email uses Keycloak VERIFY_EMAIL, UPDATE_PASSWORD and ARENA_STAFF_SETUP actions, with a 24-hour expiry. The marker provider makes the action token single-use; ordinary execute-actions links alone can otherwise be reused. SMTP acceptance is recorded as SENT, not a guarantee of inbox delivery. Password recovery remains the supported Keycloak reset flow.

## Review and validation

Screenshots and browser harness are in [artifacts/academy-mvp](artifacts/academy-mvp). Browser screenshots use isolated API fixtures; database and provider checks are separate integration tests. The temporary UI preview uses port 4202 and does not mutate live business data.

Validation results are recorded in `artifacts/academy-mvp/validation-results.json`. The disposable PostgreSQL runner is `deploy/validate-db-schema.sh`; use the test selectors recorded in the results JSON. Keycloak validation captures SMTP locally and uses test identities, never real customer email. Review screenshots include incomplete/ready dashboards, facilities, pending staff, tablet/mobile pages and dark mode.

## Results and limits

- Complete core regression: 110 discovered, 109 passed; the real Keycloak test is deliberately gated in this suite and runs separately. This includes owner onboarding/registration, licenses, trial/subscription access, billing, schema startup and academy persistence.
- Additional staff lifecycle checks: 9 passed, including old-token rejection after re-enable, safe legacy identity adoption and preserved club staff access. Final compatibility regression: 40 passed after the last authorization changes.
- Login service: 15 passed. Frontend: 47 passed; production Angular build passed.
- Browser fixture checks passed for progress, review, facilities, staff without login, explicit multiple roles, disable, Manager restrictions, Instructor route restrictions, per-sport models, and six pages at 820/390 pixels; no horizontal page overflow or JavaScript page errors. Desktop and dark screenshots were inspected.
- Real Keycloak/email/required-action results are in `keycloak-results.json`. Run `python3 deploy/validate-academy-keycloak.py` after building the provider. It needs Docker, Maven and Playwright; set `PLAYWRIGHT_MODULE` and `CHROMIUM_PATH` for your installation. Run it separately from other core Maven commands. For UI fixtures use `ARENA_MVP_UI_URL` if your preview uses another port.

No academy student, attendance or reporting business APIs existed in this checkout to extend. Those modules, full booking, school workflows, multiple owners, academy subdomains and custom role builders remain outside this feature. Current Instructor scope covers assigned sports and batches; STAFF gets a limited academy dashboard/context. Brevo inbox delivery and the real local realm installation require runtime configuration; tests captured SMTP locally and did not contact Brevo or real recipients. Existing case-insensitive identity duplicates or multiple active owners require data review before the additive unique indexes can be applied.

Review screenshots: [dashboard](artifacts/academy-mvp/dashboard-incomplete.png), [setup](artifacts/academy-mvp/setup-ready.png), [facilities](artifacts/academy-mvp/facilities-desktop.png), [staff access](artifacts/academy-mvp/staff-pending.png), [operating model](artifacts/academy-mvp/sports-operating-model.png), [mobile setup](artifacts/academy-mvp/setup-390.png).

Local dev startup correction: the dev profile enables Liquibase and validates Hibernate mappings. Restart core after updating the branch; existing databases receive pending academy migrations before requests are served. Startup tests use the profile’s migration setting rather than forcing it on.

## Setup-email failure diagnostics

Core logs the exact failing stage, classified reason, required action, realm, identity ID, HTTP status and exception classes. It does not log raw provider messages, response bodies, SMTP passwords or action tokens.

| Logged reason | Meaning / next step |
| --- | --- |
| REQUIRED_ACTION_NOT_REGISTERED | Keycloak returned 404 while looking up ARENA_STAFF_SETUP. Install the provider, restart Keycloak, register and enable the realm action. No SMTP call was made. |
| REQUIRED_ACTION_DISABLED / REQUIRED_ACTION_PROVIDER_MISMATCH | Correct the realm action configuration before retrying. |
| KEYCLOAK_ADMIN_AUTHORIZATION_FAILED | Admin credentials or management permissions were rejected (401/403). |
| KEYCLOAK_USER_OR_EMAIL_ENDPOINT_NOT_FOUND | The email request returned 404; check the identity in the configured realm and admin endpoint. |
| KEYCLOAK_REJECTED_EMAIL_OR_REQUIRED_ACTION_CONFIGURATION | Keycloak returned 400; check the recipient and required actions. |
| KEYCLOAK_EMAIL_SEND_FAILED_CHECK_SERVER_SMTP_AND_THEME_LOGS | Keycloak returned a server error during email submission. The exact SMTP authentication, connection, sender or template failure is recorded in Keycloak's server log, rather than returned to core. |

USER_ALREADY_EXISTS is a separate identity conflict (username/email already reserved locally or in Keycloak). A failed setup email leaves the existing staff identity pending; after fixing configuration use **Resend setup email**, rather than creating another staff identity.

## Automatic local Keycloak startup

Local dev now uses the provider's startup hook instead of manual Required Action registration. From `/home/prabhu/Prabhu/Projects/arenaops-infrastructure`, run:

```sh
python3 scripts/start-dev-keycloak.py
```

This builds the academy provider, preserves the existing local Postgres project/network, recreates only dev Keycloak, and applies the action, email theme and Brevo SMTP configuration automatically. Subsequent normal restarts use the installed hook. The dev Compose override must be included when recreating the container. PostgreSQL must already be running. The initial local infrastructure `.env` mail settings were synchronized from the existing local dev YAML; future Keycloak settings come from the infrastructure `.env`.

The startup hook targets `arena-dev` explicitly, handles both existing and newly created realms, and keeps Default action OFF. Isolated tests verify automatic registration on realm creation/restart, absence of duplicates, SMTP configuration, email verification, password creation, token expiry and replay rejection. Remote deployment behavior is unchanged.

Staff sign-in configuration fix: Keycloak 26 clients require the built-in `basic` default scope to emit `sub`. Local `arena-ui` and `arena-bff` were missing it, causing verified pending staff to receive ACCESS_DENIED. The local scopes were repaired and manager1 activated through the normal signed-token/BFF flow (token, context, membership all HTTP 200). Provider startup and infrastructure bootstrap/templates now preserve this scope. The disposable validation removes it before restart and checks automatic repair.
