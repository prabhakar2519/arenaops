# Superseded workflow

Business-only registration remains the default. Academy Setup MVP now adds explicit owner-managed login provisioning; see [the current workflow](ACADEMY-MVP.md). The historical implementation notes below describe the earlier registration-only change.

# Staff registration and optional login access

## Reviewed existing behavior

The academy module already has COACH/STAFF roles, owner/customer scope checks, optimistic versions, sports/batch assignments, dependency checks, and audit events. It previously required username/email, created or reused a Keycloak identity and local app_user, assigned a realm role, then sent Keycloak required-action email on every registration. Personnel edits also assigned Keycloak roles.

The existing Brevo invitation infrastructure and license activation flow belong to customer/owner onboarding. They are not a staff invitation lifecycle. The Keycloak staff helper sends required-action email but has no persisted invitation state, delivery retry ledger, staff-specific activation verification, or academy-scoped coach feature authorization. Sending this email cannot establish Active access safely.

## Implemented

- POST /api/academy/staff now registers only academy personnel, without Keycloak calls, app_user writes, or invitations. Name and existing COACH/STAFF role remain required; mobile and email can be supplied; email is optional but validated when present.
- The legacy optional username request field is accepted for compatibility but ignored. It never selects or links an account. The UI no longer asks for it.
- PUT /api/academy/staff/{id} edits personnel without identity-provider calls. Email can be edited for unlinked staff. Linked legacy email cannot be changed through personnel editing.
- Staff records with no authenticated identity can be assigned to sports/batches using the existing APIs and academy checks.
- Additive Liquibase migration 02-staff-optional-identity.xml allows null app_user_id and email. Existing foreign keys, unique academy/user link constraint, IDs, assignments, and legacy links remain intact. Rollback needs an explicit data policy because new rows may have no account/email.
- API loginAccessStatus is NOT_ENABLED for unlinked records and LEGACY_LINKED for preexisting account links. The UI labels the latter "Legacy account · review required" instead of claiming that the account is Active. Employment active/inactive remains separate.
- Existing POST /api/academy/staff/{id}/invite now checks owner scope and academy membership, then returns HTTP 409 / STAFF_LOGIN_ACCESS_UNAVAILABLE. This prevents old clients from invoking an untracked invitation. Invitation controls are removed from the UI.
- Existing Keycloak helpers are retained for compatibility but no longer called by the academy registration/update/invite APIs. No Keycloak roles, realm configuration, or existing account enabled states were changed.
- Registration/update audit events and coach dependency checks are preserved. Deactivation of personnel does not claim to disable an existing login account.

Changes are in the original repository backend and UI, and mirrored into the polished UI worktree currently served on port 4200. No owner/license/subscription/admin onboarding changes were made.

## Deferred, as permitted by the request's scope-control requirement

Enable Login Access, invitation pending/expired/active/disabled status, resend, and access disablement require a dedicated lifecycle before they can be exposed. Required follow-up:

1. Persist academy/staff invitation identity, hashed single-use token, expiry/consumption, unique outstanding invitation, delivery state, and audit history separately from owner license codes.
2. Validate owner scope, staff email and legacy links; serialize duplicate requests; avoid silently reusing identities from other academies or granting roles to owners/admins.
3. Reuse Brevo transport with a staff activation template, transactional delivery/outbox and retry compensation. Keep delivery failure separate from activation and prevent duplicate accounts on retries.
4. Use Keycloak-supported credential setup and verified completion, linking the authenticated subject only after successful activation. Never store plaintext credentials or publish administrative secrets.
5. Authorize coach/staff features against academy membership, not realm role alone. Existing academy management endpoints are OWNER-only and remain so.
6. Implement disablement/revocation with session handling and audit, without deleting personnel or revoking unrelated academy memberships.
7. Audit existing automatically provisioned legacy users separately; do not delete or disable them blindly during migration.

Until that work is completed, optional account links are a foundation only. New staff cannot independently log in merely by being registered.

## Applying the backend change

Restart arena-core with Liquibase enabled after review of pending migrations. The previously running development process had Liquibase disabled; restarting with that flag will not apply the nullable-column migration. The UI refresh alone does not update the running backend. This implementation has not modified the live database or sent real invitation emails.

## Validation

- Polished UI: production build and all 43 frontend tests pass.
- Original UI: production build and all 35 frontend tests pass.
- Chromium checks with isolated API fixtures pass at desktop/tablet/mobile widths and in light/dark mode, including registration without email or username and no invitation request, sports registration, staff management, batch coaches/schedules, and route authorization.
- Disposable PostgreSQL: both schema startup/legacy migration tests, all 16 training persistence tests and 15 registration/identity/schedule unit tests pass. They cover optional-email database writes with no app_user creation, assignment of unlinked coaches, scope checks, dependencies, version conflicts and preserved scheduling.
- The initial broader backend suite reported BrevoEmailServiceTest and LocalEnvironmentConfigTest failures outside the staff changes. Migration-fixture expectations were updated for the new migration and for development Liquibase being disabled by local configuration; focused schema checks explicitly enable Liquibase.
- No lint script is configured. Live staff records, live Keycloak users and real email delivery were not used for validation.
