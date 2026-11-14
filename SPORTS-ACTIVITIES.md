# Sports & Activities — MVP v1.0 implementation report

## Custom-registration error follow-up (12 October 2026)

The landing-page error banner incorrectly offered Review training batches for every error, including unsupported-method responses from registration. That action is now restricted to `TRAINING_DEPENDENCY_EXISTS`. Closing the catalogue (including Escape) clears the registration error while retaining the selected activities for retry. A registration HTTP 405 now explains that the running backend does not support activity registration.

The source maps `POST /api/academy/sports/bulk` and the Login proxy forwards the request method unchanged. The screenshot's unsupported-method response is consistent with a stale Core artifact or a proxy route mismatch; the exact runtime cause requires the failed Network URL/method/status and deployed Core version. No fallback to sequential registration was added, because it would compromise atomic registration. No services were restarted.

Two regression tests cover dismissal/selection retention and dependency-only navigation. Frontend tests now total 85 passing tests.

## Workspace and scope

Branch: `feature/sports-activities-mvp-v1`. It was created from the current application revision `6977e29`, rather than local `main`: local main contains only the initial repository revision and lacks the application. Existing uncommitted work was preserved. Nothing was committed or pushed.

No services or containers were started, stopped, restarted, recreated or deployed. No live database changes were applied. Keycloak, invitations, licensing, billing, owner onboarding, student registration and attendance were not changed by this task.

## Result

- Registered activities are the landing-page focus, with total/active counts and Add Activity. Search/status filters appear only above eight registered activities, including inactive records.
- The responsive catalogue opens on demand, uses the existing catalogue/categories/icons, searches across categories, supports multiple predefined and custom selections, and keeps its action footer outside the scrolling choices. Registered activities remain disabled, including inactive activities; catalogue reactivation remains deferred.
- Registration sends one atomic bulk request. New activities are active with both capabilities disabled. All entries are validated before writes; indexed validation feedback preserves the selection after failure.
- Custom names use shared whitespace/case normalization and length/duplicate validation. Rename retains the activity identity and related records. Predefined activities cannot be renamed. No deletion API or UI was introduced.
- Configuration has its own route, independent Training and Play/Facilities switches, explicit Save/Cancel, unsaved-change protection and version-conflict handling. Neither, either or both capabilities are valid.
- Deactivation and Training disable check active recurring batches against academy-local dates and schedule effective dates, even when sessions have not been generated. Historical batches and unused coach/facility configuration do not block. Reactivation preserves capabilities.
- Owner and both Manager modes use existing database membership authorization. Instructors can read assigned activities but cannot administer them. Tenant and version checks remain server-side; setup calculations are unchanged.
- Dialogs support keyboard focus containment, Escape and focus restoration.

## Files changed for this task

Backend:

- `arena-core/src/main/java/com/arena/core/training/TrainingService.java`
- `arena-core/src/main/java/com/arena/core/training/TrainingController.java`
- `arena-core/src/main/java/com/arena/core/training/TrainingRequests.java`
- `arena-core/src/main/java/com/arena/core/training/AcademySport.java`
- New `arena-core/src/main/java/com/arena/core/training/ActivitySelectionException.java`
- `arena-core/src/main/java/com/arena/core/exception/GlobalExceptionHandler.java`
- `arena-core/src/main/resources/db/changelog/db.changelog-master.xml`
- New `arena-core/src/main/resources/db/changelog/features/R1_10_academy_training/05-activity-capabilities.xml`
- `arena-login/src/main/java/com/arena/login/exception/ErrorCode.java` preserves duplicate/dependency error codes through the proxy.

Frontend:

- New `arena-ui/src/app/training/sports-activities.component.{ts,html,scss}`
- New `arena-ui/src/app/training/sport-configuration.component.ts`
- New `arena-ui/src/app/training/activity-catalogue.ts`
- `arena-ui/src/app/training/training.component.{ts,html}` retires the old inline catalogue/configuration drawer and aligns legacy registration with the bulk API.
- `arena-ui/src/app/training/training.service.ts`
- `arena-ui/src/app/app.routes.ts`
- `arena-ui/src/app/sidebar/sidebar.component.ts` (inline navigation template).
- `arena-ui/package.json` and the training/business-profile test scripts.

The earlier missing-logo report is addressed separately by new `arena-ui/src/app/ui/business-logo.component.ts`, integrated in Header and Business Profile component TypeScript/templates. It retrieves the normalized PNG through credentialed HttpClient, releases object URLs and falls back to an initial on failure. A browser Network response is still needed to establish the original runtime failure; this change is not a claim that the server currently returns a valid image.

Tests added: `SportLifecycleTest.java`, `ActivityRegistrationTransactionTest.java`, `scripts/test-sports-activities.mjs`. The existing optional `TrainingPersistenceTest.java` expectation now permits club activity metadata while retaining academy-only operational restrictions.

## APIs and navigation

- `GET /api/academy/sports/{id}` — tenant/assignment-scoped detail.
- `POST /api/academy/sports/bulk` — `{ activities: [{ catalogueCode, customName? }] }`, all-or-nothing, indexed errors.
- `PUT /api/academy/sports/{id}/name` — custom rename with version.
- Existing versioned `PUT /api/academy/sports/{id}` — capabilities and status.
- `/academy/sports` — registered activities and catalogue dialog.
- `/academy/sports/:id/configure` — explicit configuration.
- Existing `/academy/batches`, `/academy/staff` and facility workspace routes are reused; no new operational screens were added.

## Database and concurrency

The additive changeset 05 removes only the legacy CHECK requiring Training OR Sessions. It preserves existing rows, capabilities, registration keys and relationships. The existing unique academy/registration-key constraint remains in place. Apply this migration before accepting new unconfigured registrations or saves with neither capability enabled. PostgreSQL migration execution remains a manual deployment verification requirement.

Bulk registration runs in the existing service transaction and takes the academy customer row's pessimistic lock. Validation occurs before writes; transaction rollback handles persistence failure. Isolated H2 tests exercise the real transactional service proxy, rollback after flushed writes and two simultaneous identical submissions. These tests supplement, rather than replace, PostgreSQL runtime verification.

## Validation results

- Frontend `npm test`: **83 passed**, zero failures.
- Frontend `npm run build`: passed.
- Core full suite before the final small audit/instructor assertion changes: **160 discovered, 130 passed, 30 skipped**, zero failures/errors.
- Final focused Core suite: **19 passed** (12 lifecycle/authorization, 3 transaction/concurrency, 4 setup), zero failures/errors.
- Login suite: **18 passed**, zero failures/errors.
- `git diff --check`: passed. New Liquibase XML parsed successfully.
- Tests cover duplicate normalization, tenant isolation, instructor restrictions, Manager access, neither-capability configuration, optimistic locking, recurring/historical dependencies, replay registration, atomic rollback, concurrent registration, selection retention, no auto-save, unsaved changes and authenticated logo loading.
- Live PostgreSQL tests are opt-in and were not run. No browser, live Keycloak, external booking or deployed logo response verification was performed.

## Remaining limitations and deployment checks

1. This repository contains facility configuration but no operational booking/session dependency API for courts/tables. Facility configuration alone must not block capability changes. If bookings exist in another service, disabling Play/Facilities cannot be declared safe for those external commitments until an approved dependency check is integrated. No booking workflow was invented.
2. Clubs can register/configure activity metadata without changing their organization type or licensed sport entitlements. Existing batches/staff/facility workspaces remain academy-only. Configuration explicitly reports those unavailable workspaces; no unsupported placeholder screens were added.
3. ACTIVE batches with missing schedules are conservatively treated as dependencies until their inconsistent data is corrected.
4. The existing setup calculation counts registered activities and introduces operational requirements according to enabled capabilities. That calculation was deliberately preserved.
5. Catalogue reactivation remains deferred. Use the registered activity's Reactivate action.
6. Confirm the actual saved logo response in DevTools; non-PNG/error responses correctly show the fallback rather than a broken image.

The user should deploy the migration and updated **Arena Core, Arena Login and Arena UI** artifacts, then perform runtime checks. Keycloak changes/restart are unnecessary for this task. PostgreSQL does not require a restart for this DDL. Validate registration rollback/concurrency on PostgreSQL, Manager permissions, ongoing-batch rejection, responsive keyboard navigation, and logo retrieval on both localhost and the LAN origin. No deployment/restarts were performed here.
