# BUG-002 — Duplicate staff login identifiers and partial registration

Verified 2026-11-14. No live club data or Keycloak realm was modified.

## Root cause traced

1. Angular `TrainingComponent.saveStaff` in `arena-ui/src/app/training/training.component.ts` previously POSTed `staff` and, only after success, called `enableLogin` against `staff/{id}/login`.
2. `TrainingController.staff` → `TrainingService.addStaff` (`arena-core/src/main/java/com/arena/core/training/TrainingService.java`) registers personnel only. It deliberately sets `active=true`, saves/flushed the membership and commits its own transaction. Login access is optional.
3. `StaffLoginService.enable` then checked local `app_user` / staff login usernames, mutated login provisioning state and called `KeycloakService.provisionAcademyStaff`. Keycloak-only duplicate identities were detected at this later stage. Username/email conflicts were combined into `USER_ALREADY_EXISTS`.
4. The second request could return an error although the first staff-creation request had already committed. No transaction could undo that separate request. The login service additionally uses `noRollbackFor=ArenaOpsException` intentionally to retain provisioning and invitation failure states for existing personnel.
5. Employment and login are separate lifecycles. Ordinary staff may have no `appUserId`, optional contact email, `NOT_ENABLED` login and an active personnel membership. Provisioned logins remain `PENDING` / locally inactive until verified Keycloak required actions complete. This remains intact.

## Identifier policy and safe validation

The authoritative application identity policy is in `R1_10_academy_training/03-academy-mvp.xml`: case-insensitive global username and normalized-email unique indexes on `app_user`, plus staff username/subject uniqueness. Therefore ArenaOps requires a unique account email even if a realm happens to allow duplicate emails. Contact-only staff emails retain their existing optional/non-unique behavior.

`StaffLoginService.assertAvailable` now checks local accounts and reserved staff login intents, then invokes `KeycloakService.validateAcademyStaffIdentifiers` against the configured realm. Keycloak username and exact-email searches are filtered case-insensitively. Own-identity recovery requires the exact academy ID, durable staff ID, matching email and no service account; full user representations are read before trusting ownership attributes. Keycloak creation conflicts are rechecked to classify races. A provider conflict that cannot safely be classified remains a generic conflict rather than inventing a cause.

The global Core error handler and BFF safe-error mapping return:

- `DUPLICATE_USERNAME` (409): “This username is already in use. Choose another username.”
- `DUPLICATE_EMAIL` (409): “An account already uses this email address. Enter a different email.”

Both include deterministic `fieldErrors.username` or `fieldErrors.email`, the existing correlation ID and normal error structure. Provider bodies, subject IDs and account details are not returned. Availability is authenticated and restricted to the existing Owner / Full Operations Manager personnel policy. Actual Spring Security-chain and BFF tests reject anonymous requests before lookup.

Angular validates formats, checks on blur only when both identifiers are syntactically valid, caches the current result and checks before submission when necessary. Checking/submitting disables Add Staff. Corrections clear the relevant error, stale responses cannot replace corrected state, and closing the editor cancels a pending preflight continuation. Failed submissions retain values and the request UUID. No client-side staff row is created optimistically.

## Cross-system consistency

Login-enabled Add Staff now uses `StaffRegistrationService.register` through a single combined API. It does not chain the personnel-only API and login API.

1. A short transaction locks the academy, validates permissions/identifiers and persists an **inactive** personnel intent with the original request UUID, normalized identifiers, roles and a payload fingerprint. Existing rows are not converted.
2. Another transaction takes the academy lock, validates availability again and provisions/retrieves only the Keycloak identity owned by that exact intent. It persists the locally inactive `app_user`, subject link, `PENDING` login and active personnel membership together. Activation is committed only after provisioning succeeds.
3. Remote failure rolls back local activation and records `PROVISIONING_FAILED` on the inactive intent. A database rollback after remote creation retains the committed intent as the identity's recovery anchor. No remote identity is deleted, adopted from another person or modified as cleanup.
4. Retrying the same UUID and payload returns the completed member or resumes the same inactive intent. Changed payloads under the same UUID are rejected. Academy row locks plus existing username and new email/request-key constraints prevent duplicate membership creation under concurrency, including different academies racing for one email.
5. A lost/unknown database result uses `STAFF_REGISTRATION_UNCERTAIN` with instructions to retry the same operation. It does not claim that creation definitively failed. The service re-reads committed state before marking failure; a completed registration is returned as completed.
6. Setup email is sent **after** the registration transaction commits. Delivery failure returns the created member with `notificationState=FAILED` (or the persisted unsent state), a shared warning and the existing resend action. It is not reported as a failed create.

An incomplete intent remains visibly inactive and can be recovered with **Retry login setup** in its existing staff row, including after reload. Contact/role edits of an incomplete intent are rejected to preserve its immutable retry payload. Completed memberships retain their normal edit behavior. Editing personnel contact information does not provision or update Keycloak credentials; linked email remains read-only under the pre-existing rule. Existing staff without login continue using the original personnel-only API and explicit Enable Login Access workflow.

## API contracts

All endpoints use the existing authenticated BFF proxy and Core tenant/permission checks; no public identity lookup was added.

- `POST /api/academy/staff/login-availability`: `{username,email,requestId?,staffId?}` → `{available:true}`, or safe field-specific conflict. Optional IDs may refer only to an authorized own-tenant intent/member.
- `POST /api/academy/staff/registration`: `{requestId,name,email,phone?,role,designation?,username,roles}` → 201 with the actual persisted member. `requestId` is a UUID; retry identical normalized payloads with the same key.
- `POST /api/academy/staff/{id}/registration/retry`: reuses the persisted original intent → actual persisted member.
- Existing personnel create/update and explicit login-management contracts remain available.

## Database migration

Added `R1_10_academy_training/07-staff-registration.xml`, included by `db.changelog-master.xml`:

- Nullable `registration_request_id VARCHAR(36)`.
- Nullable `registration_hash VARCHAR(64)`; excluded from JSON responses.
- Nullable normalized `login_email VARCHAR(255)` for reserved login identifiers.
- Unique `(academy_id,registration_request_id)` and unique non-null `login_email` indexes.

The migration is additive; it does not rewrite existing staff, contact emails, identity links or financial data. Apply it through the normal Core Liquibase deployment before using the updated application. It has not been applied to live PostgreSQL in this task.

## Files changed

Core:
- New `training/StaffRegistrationService.java` and `training/StaffRegistrationController.java`.
- `training/StaffLoginService.java`, `training/StaffLoginController.java` (error documentation).
- `training/AcademyStaff.java`, `training/AcademyStaffRepository.java`, `training/TrainingService.java`.
- `service/KeycloakService.java`.
- `exception/ErrorCode.java`, `exception/GlobalExceptionHandler.java`.
- Liquibase migration/master listed above.

BFF:
- `arena-login/src/main/java/com/arena/login/controller/TokenController.java` (safe duplicate field errors).
- `arena-login/src/main/java/com/arena/login/exception/ErrorCode.java`.

Angular:
- `arena-ui/src/app/training/training.component.ts`, `.html`, `.scss`.
- `arena-ui/src/app/training/training.service.ts`, `arena-ui/src/app/api-error.ts`.
- Only targeted field-error styling/associations and incomplete-registration actions were added. Existing staff layout, Inter, themes and role-permission behavior remain in use.

Tests/documentation:
- New Core `training/StaffRegistrationConsistencyTest.java`, `training/StaffRegistrationApiTest.java`.
- Core `training/StaffIdentityTest.java`, `training/StaffLoginServiceTest.java`, `training/AcademyKeycloakIntegrationTest.java` (updated expected conflict code), `config/SecurityErrorContractTest.java`.
- Core `documentation/OpenApiContractTest.java` (new controller export coverage).
- New BFF `controller/StaffRegistrationProxyTest.java`.
- `arena-ui/scripts/test-training.mjs`; new `arena-ui/e2e/specs/staff-registration.spec.ts`.
- Generated `docs/api/arenaops-core-openapi.yaml`, `arenaops-login-openapi.yaml`, `core-source-review.json`, `login-source-review.json`.
- This report, `results.json` and light/dark screenshots.

## Executed verification

- Angular production build and Playwright TypeScript check: passed.
- Frontend tests: **208 passed**, zero failures.
- Core functional/security tests: **48 passed**. This includes 14 real-transaction H2 consistency tests, actual unique-constraint failure after remote provisioning, local rollback, unchanged existing account, safe retries, concurrent same/different request keys and cross-academy email races.
- BFF registration forwarding/error/auth tests: **3 passed**; existing token-error regression: **1 passed**.
- API documentation/security tests: **6 passed**. Schema/ref/example/source coverage validated: Core 136 operations; BFF 21 operations.
- Browser: **44 passed** across staff registration, shared-dialog and sport regressions in desktop light and tablet dark. After the final field-association/cancellation refinement, **26 staff browser cases passed** on the final build, including all 24 prior staff cases and the new cancellation scenario in both projects. **46 distinct browser cases** passed across these runs.
- Final light/dark screenshots inspected; errors remain beside their fields without introducing an extra grid cell.
- Liquibase XML well-formedness and `git diff --check`: passed.

Initial browser failures were test-fixture/selector assumptions (empty-list heading ambiguity, a stale fixture loginAccessStatus, validation triggered by a preceding valid blur and clicking behind an open editor). These were corrected; the final executed checks have no remaining failures. Expected database unique-constraint errors in the backend test log are deliberate fault-injection assertions, not test failures.

## Remaining limitations

- Browser APIs and Keycloak Admin API responses are mocked; no live realm/provider/SMTP integration was executed. The existing opt-in `AcademyKeycloakIntegrationTest` was not executed without an isolated Keycloak environment.
- Database tests execute real H2 transactions and uniqueness constraints. They do not substitute for applying the PostgreSQL Liquibase migration in an isolated deployment validation environment.
- Cross-system provisioning cannot be one atomic database transaction. An interrupted operation intentionally retains a tracked **inactive** intent; a created Keycloak identity remains tied to that intent and cannot activate ArenaOps access without the local link. Recovery is explicit, not an automatic background worker.
- An actual external identifier conflict introduced mid-provisioning may require operator reconciliation before retry can finish. The existing account is never overwritten/deleted. Incomplete intent payloads are immutable; complete/recover the original operation before editing contact details.
- No production migration, service restart or shared-data cleanup was performed. Existing partial staff records from the old workflow are preserved and are not automatically deleted or merged.
