# Sports & Activities final integration — 12 October 2026

## Review and coverage

Continued on `feature/sports-activities-mvp-v1` and preserved existing uncommitted work. Steps 1–20 already supplied the registered-activity landing, atomic bulk registration, shared normalization, indexed validation, versioned rename/configuration/status, recurring-batch dependencies, member authorization and auditing. No replacement backend workflow or new branch was introduced.

The final requirements are integrated into the existing components:

- Catalogue entries distinguish **Already added** from **Inactive**; both stay disabled. Reactivation remains exclusively in Registered Activities. Backend duplicate checks include inactive records and tenant-serialized concurrent requests.
- Close, Cancel and Escape with selections or a nonblank custom draft offer **Keep Selecting / Discard**. Discard clears the selection and draft. Outside clicks do not dismiss. Successful registration closes automatically.
- Desktop uses two compact columns, mobile one column and a near-full-screen modal. The scrolling body retains its footer; smaller screens use labelled compact registered rows. Shared activity icons have fixed alignment; custom activities use a generic diamond. Existing predefined icons/initial fallbacks are retained. Selected entries have explicit selected text and aria-pressed, not color alone.
- Loading placeholders are distinct from empty/search-empty states. A failed load suppresses the empty state and Add action, with Retry. Existing safe API descriptions explain permissions and failures.
- A lost response or server failure triggers a current-data refresh before retry. Already persisted selections are removed by catalogue identifier or normalized custom name. If reconciliation fails, registration stays disabled until refresh succeeds. Backend uniqueness remains authoritative, including in-flight or concurrent submissions.
- Configuration links require an active sport, a persisted enabled capability and its current switch remaining enabled. Unsaved newly enabled settings do not expose operational links before Save. Unsupported facility/club workspaces remain explained without fake links.
- Dashboard shows **Manage Activities** when the SPORTS step is incomplete. Setup calculations and the six-step workflow are unchanged.

## Backend, authorization and history

No production backend API or schema changes were needed for this integration. Existing `POST /api/academy/sports/bulk`, versioned activity APIs and exception handling are reused. New activities can be active with neither capability; inactive sports do not satisfy setup. Existing batch/facility APIs enforce active sports and relevant capability prerequisites. Disabling capabilities preserves configurations and historical records; it does not reopen records or delete links.

`AcademyAuthorization.actor/manage` re-reads the persisted user and staff membership on every request. Owner and both Manager modes may administer sports; coaches have only assigned reads. Manager-role removal applies to the same JWT on the next request. Tenant association, active membership, subject matching and activity version are server-checked. The route guard hides administrative screens from coaches; a stale UI session can still display controls until its normal authorization refresh, but cannot bypass backend revocation.

Lifecycle audit records retain actor, academy, record identity and before/after capabilities/status. Owner setup review audit history remains untouched. The mutable current review flag is deliberately invalidated by the existing workflow when prerequisites disappear; its previous confirmation audit record is not deleted. No new historical completion table was introduced.

## Files modified

- `arena-ui/src/app/training/sports-activities.component.ts`: modal discard handling, catalogue labels, load state, reconciliation and focus handling.
- `arena-ui/src/app/training/sports-activities.component.html`: confirmations, retry, placeholders, labels, footer and compact-row metadata.
- `arena-ui/src/app/training/sports-activities.component.scss`: two-column desktop, single-column mobile, compact table rows, icon alignment and loading placeholders.
- `arena-ui/src/app/training/activity-catalogue.ts`: generic custom icon fallback.
- `arena-ui/src/app/training/sport-configuration.component.ts`: contextual link checks and failed-detail retry.
- `arena-ui/src/app/academy/academy.component.ts`: Manage Activities dashboard action.
- `arena-ui/scripts/test-sports-activities.mjs`: final integration regressions and updated discard expectation.
- `arena-core/src/test/java/com/arena/core/training/AcademySetupTest.java`: active custom/unconfigured and all-inactive/reactivation setup tests.
- `arena-core/src/test/java/com/arena/core/training/ManagerAuthorizationTest.java`: role revocation on subsequent request.
- `arena-core/src/test/java/com/arena/core/training/SportLifecycleTest.java`: stale-version write rejection.
- This report. Earlier implementation changes remain described in `SPORTS-ACTIVITIES.md`.

## Catalogue findings

The existing seed has 42 entries and no duplicate normalized names. Identifiers were preserved. Suggested separate product review: clarify whether Pool/Billiards should remain combined; Gym/Fitness and Athletics/Running intentionally overlap at different levels; consider Carrom, Shooting and Zumba if required by target academies. Nothing was inserted, renamed or remapped automatically.

## Automated validation

- Frontend full test chain: 91 passed; the additional contextual-link assertion then passed in the focused 27-test sports suite (92 distinct passing frontend tests in total).
- The 29-test training/dashboard suite was rerun after the final Dashboard action change.
- Angular production build passed after the final source changes.
- Core focused compilation/test run: 28 passed, zero failures/errors/skips: 5 setup, 13 sport lifecycle, 3 atomic/concurrency and 7 Manager authorization tests.
- `git diff --check` passed.
- Commands: `npm test`, `node scripts/test-sports-activities.mjs`, `node scripts/test-training.mjs`, `npm run build` from `arena-ui`; `mvn -q -f arena-core/pom.xml test -Dtest=AcademySetupTest,SportLifecycleTest,ActivityRegistrationTransactionTest,ManagerAuthorizationTest` from the repository root.
- No live PostgreSQL, Keycloak, browser/device or external booking tests were run. Static responsive assertions do not substitute for visual runtime verification.

## Known limitations and runtime checks

- Facility configuration exists, but this repository has no booking commitment service. Training commitments are checked, including future recurrence without generated sessions. External facility bookings cannot be verified or protected without an integration contract. If such bookings exist, the minimal solution is a server-side dependency check before capability disable/deactivation; this remains an explicit functional/security gap, not a claim of full operational protection.
- Clubs can administer activity metadata, but existing staff/batch/facility operations remain academy-only. This integration preserves their licensing and organization type; supporting those operations for clubs requires a separately approved backend scope change.
- Responsive behavior is verified through compilation and stylesheet assertions, not live browser/device rendering. Manual keyboard, tablet and mobile verification is still needed.
- PostgreSQL concurrency and migration tests require the user's runtime environment. Isolated transactional tests use H2 and do not start services.
- The reported HTTP 405 is not resolved by UI integration. The compiled Core controller contains the bulk POST mapping. Verify that the deployed Core artifact includes it and that the runtime proxy forwards to that Core instance. Do not fall back to sequential writes.

## Deployment

No services or containers were started, stopped, restarted, recreated or deployed. No commits, pushes, merges, live database changes or Keycloak changes were made.

No new migration in this integration. The earlier `05-activity-capabilities.xml` migration is still required if not yet deployed, to remove the legacy capability CHECK while preserving records. Apply through the normal Liquibase deployment process before accepting unconfigured activities.

Deploy the updated **Arena UI** build. **Arena Core** requires rebuild/deployment if the previous bulk-endpoint implementation is absent (the reported 405); the integration itself changes Core tests only. **Arena Login** requires deployment only if the earlier sports-error mapping changes are absent. No Keycloak or PostgreSQL restart is required by this integration. The user handles every deployment/restart.
