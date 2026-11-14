# Facilities & Resources — implementation report

## Scope and decisions

Work is on `feature/facilities-resources-mvp-v1`, created from the existing application branch after inspecting Git state. All unrelated uncommitted work was preserved. No services/containers were started, stopped, restarted or deployed; no live migrations, commits, pushes or merges were performed. Keycloak was not changed.

**Approved scope adjustment:** the user selected **“Complete facilities; defer session integration.”** ArenaOps contains no Running Sessions backend. Accordingly this delivery implements persistent facility management; it does not introduce session start/end, booking, timing, rate-snapshot persistence or billing APIs. Acceptance criteria concerning live sessions and deactivation/session races remain deferred, not claimed as passed.

Pricing policy implemented explicitly: a positive hourly rate is required for configuration completeness. Frame rate is optional and positive when supplied for Snooker table types. Courts support hourly pricing only. Null hourly price returns a facility to unconfigured. This establishes configuration data, not an approved frame-session billing workflow.

Pool/Billiards is a type under registered **SNOOKER**, not a top-level facility tab. An independently registered `POOL_BILLIARDS` activity remains in Sports & Activities, but does not create another facility tab or get silently converted to Snooker.

## Inspection and gaps

Reused ArenaOps authorization (`AcademyAuthorization`), registered sports/capabilities, `TrainingRecord` identity/version/timestamps, `AcademyPlayArea`, global errors, Login proxy and `AuditService`.

The existing SPMS source is `/home/prabhu/Prabhu/Projects/SPMS`:

- `spms-ui/src/app/manage-tables/manage-tables.component.{ts,html,scss}`: shared table/court layout, quantities, type names, E/F/P/SE/C prefix concepts, responsive cards and sticky summary footer.
- `spms-core/src/main/java/com/arena/core/{controller/GameTableController.java,service/GameTableService.java,entity/GameTableEntity.java,entity/RateCardEntity.java}`: legacy facility/price contracts.
- `spms-core/src/main/java/com/arena/core/service/SessionService.java`: session pricing inspected read-only. Stop-session code looks up the current type-wide rate, so copying it would not satisfy rate snapshots.
- Legacy migrations `R1_0_tables/02-create-game-table.xml`, `04-create-rate-card.xml` and sport-column migrations were inspected previously; none was copied or executed.

SPMS type-wide rates and local code generation were incompatible with the new requirements. The existing ArenaOps preview had no persistence, versions or durable operation identities. These parts were replaced while retaining the layout. Authentication and SPMS session/billing code were not copied.

## Data and migrations

New additive changelog:

`arena-core/src/main/resources/db/changelog/features/R1_10_academy_training/06-facility-resources.xml`

It extends **existing `academy_play_area`** with nullable `facility_type`, `display_name`, `hourly_rate NUMERIC(12,2)` and `frame_rate NUMERIC(12,2)`. Existing numerical primary keys, codes, names, capacity, tenant/sport FKs, active flags and optimistic versions remain intact.

Additional tables:

- `facility_code_counter`: primary key `(academy_id,sport_id,facility_type)`, transactional last-number counter and tenant-scoped sport FK.
- `facility_operation`: primary key `(academy_id,operation,request_key)`, sport FK, request fingerprint, original response and creation time.

The existing stronger **tenant-wide unique facility-code constraint** is preserved, rather than weakening it to sport/type uniqueness. Allocation considers existing legacy and inactive codes across that tenant. New lookup indexes cover sport listing and operation age.

No existing record is deleted, renumbered, imported from SPMS or assigned a guessed table type. Legacy play areas without a concrete type are returned with their original code/name and remain read-only in the new editor. Explicit type mapping/conversion requires a reviewed migration or API contract. The legacy API remains usable for compatible legacy metadata edits but cannot modify newly managed records. Legacy creation of supported operational sports is blocked, and existing codes/sport associations cannot be changed through that old path. This closes the client-supplied-code bypass.

Completeness is derived from managed type plus positive hourly price; no READY/configuration lifecycle column was added. New managed facilities do not count toward the existing facility setup step until pricing is configured. Legacy setup behavior is preserved. Facility mutations re-evaluate existing academy setup rules in the same transaction; single-sport clubs are not converted into academies.

## API contracts

All endpoints use the existing JWT security chain and server-side active membership authorization. Owners and Managers use existing facility-management permissions. Academy IDs in paths are compared with the persisted authorized tenant; they never authorize access themselves.

### Context and listing

- `GET /api/v1/academies/current/facilities/context`
  - Derived tenant ID, registered sports and generation limits.
- `GET /api/v1/academies/{academyId}/sports/{registeredSportId}/facilities`
  - All active/inactive, configured/unconfigured facilities for that tenant/sport, ordered by database ID.

### Creation

`POST /api/v1/academies/{academyId}/sports/{registeredSportId}/facilities/bulk`

Header: `Idempotency-Key: <UUID>`

```json
{"facilities":[{"facilityType":"ENGLISH","count":3},{"facilityType":"POOL","count":2}]}
```

Returns HTTP 201 with `{ "facilities": [...] }`, including numerical IDs, immutable generated codes, null initial prices, active=true, configured=false and version=0.

Supported types: SNOOKER → ENGLISH, FRENCH, POOL, SEMI_ENGLISH (retained from SPMS); BADMINTON → COURT. Unknown fields, client IDs/codes, duplicate types, unsupported types and non-positive/fractional quantities are rejected. All entries validate before allocation/writes.

Configurable properties: `arena.facilities.max-per-type` defaults to 50; `arena.facilities.max-bulk` defaults to 200. Each configured limit must be within 1–1000. The UI receives these limits from context.

### Configuration

`PATCH /api/v1/academies/{academyId}/facilities/bulk`

```json
{"facilities":[{"facilityId":101,"version":0,"displayName":"AC room","hourlyRate":200,"frameRate":150},{"facilityId":102,"version":0,"hourlyRate":150}]}
```

Every entry requires ID/version and at least one configuration field. Missing fields are unchanged. Explicit null clears display name/frame price; null hourly price means unconfigured. Blank names normalize to null. Active must be a non-null boolean. Codes, types, IDs and tenant/sport association cannot be changed through PATCH.

Rates use exact BigDecimal parsing and NUMERIC, reject non-positive values/unsupported court frame prices, and allow at most two meaningful decimal places up to 9,999,999,999.99. No binary floating-point money conversion occurs in the backend JSON parser.

The response contains updated records and current versions. One stale version rejects the entire batch with `FACILITY_VERSION_CONFLICT`, `STALE_VERSION` and authorized resource/version details. UI drafts remain intact.

### History

`GET /api/v1/academies/{academyId}/facilities/{facilityId}/audit`

Returns the latest 200 facility events after tenant/facility ownership checks. Audit events contain server principal, tenant, facility metadata, timestamp and JSON before/after values. History is appended, never overwritten by facility updates. No audit mutation endpoint was added.

## Locking and idempotency

1. Existing authorization acquires the tenant customer row lock. This provides conservative tenant-wide serialization consistent with the existing MVP model.
2. Missing counter rows initialize using PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`.
3. Counter rows are locked with `SELECT ... FOR UPDATE` in sorted facility-type order.
4. Allocation starts after both the durable counter and any matching existing legacy/inactive code. Facilities, counters, audit and operation response commit or roll back together.
5. PATCH locks facility rows in ascending ID order, then rechecks submitted versions before updates. JPA `@Version` remains the database-enforced conflict check.
6. Creation fingerprints include academy, sport and a sorted type/count representation. Same-key/same-payload replay returns the original creation result without re-creating records or success audit events. Changed payload/sport with the same academy-scoped CREATE key returns 409.

READ COMMITTED remains unchanged. There is no Java synchronized lock, Redis or external idempotency infrastructure.

**Retention policy:** retain CREATE operation records indefinitely for MVP. No automatic expiry/cleanup can turn an old successful key into a new creation. Future retention changes need an explicit replay/tombstone policy and DTO compatibility review.

The UI generates one cryptographically random UUID per intentional operation and stores the pending key/payload in sessionStorage before sending. HTTP failures with an uncertain result retain the key, including across refresh. Retry never replaces it with a fresh UUID. GET loads persisted facilities on reopening; replay merges returned IDs without duplicating rows. Session-storage failure blocks generation. No tokens or credentials are stored in this operation state.

## Angular integration

`FacilityResourcesComponent` replaces the old mock component at `/academy/facilities`:

- Tabs derive exclusively from registered SNOOKER/BADMINTON activities; one supported sport shows directly, two show tabs, zero shows appropriate no-sports/unsupported guidance.
- Inactive/capability-disabled sports retain historical facilities. Generation/configuration/reactivation require saved active capability; safe deactivation remains possible.
- Type/count generation stores resources immediately, independent from later price edits. POST rows update the screen without an additional GET.
- Independent name/hourly/frame fields and Active controls reuse the SPMS cards/footer.
- Only changed fields and submitted versions are PATCHed. Generation is blocked while existing rows have unsaved edits, preventing their loss when returned rows are merged.
- Server validation messages attach to matching fields; client errors remain beside fields. Whole-batch failure preserves inputs.
- Conflict/uncertain-save state offers Review Latest Changes. Latest values display separately; replacing the draft requires explicit confirmation. No automatic merge/retry against a new version.
- Deactivation and rate edits ask for confirmation. The rate notice states that Running Sessions integration is deferred.
- Existing route-leave/query-context guards, Cancel, beforeunload protection and responsive layout are retained.
- Sports & Activities and Facilities & Resources remain visible for authorized Owners/Managers even when no sports exist or the business is a single-sport club. Coach/Staff administrative restrictions remain unchanged.

## Deferred operational work — important

No Running Sessions or booking writer exists for these facilities in ArenaOps. Deactivation currently changes facility configuration under a row lock; it cannot race with a nonexistent ArenaOps session writer. It does **not** inspect external SPMS bookings/sessions.

`requireEligibleLocked` is a reusable guard requiring an existing caller transaction. It locks the facility row and checks tenant, saved active capability, active flag and pricing completeness. It does not start sessions, select billing modes or store rate snapshots.

Before enabling any operational session/booking writer, that writer MUST take the same facility lock, persist applicable rate snapshots in its transaction, and implement commitment validation on deactivation and sport/capability disable. The current deactivation hook has no commitment query because the integration was expressly deferred. Session race/snapshot requirements are unimplemented, not validated by these tests. Do not connect an external writer and assume those protections already exist.

## Files changed for this task

Added:

- `arena-core/src/main/java/com/arena/core/facility/FacilityContracts.java`
- `arena-core/src/main/java/com/arena/core/facility/FacilityFailure.java`
- `arena-core/src/main/java/com/arena/core/facility/FacilityLedger.java`
- `arena-core/src/main/java/com/arena/core/facility/FacilityService.java`
- `arena-core/src/main/java/com/arena/core/facility/FacilityController.java`
- Changelog 06 above.
- `arena-core/src/test/java/com/arena/core/facility/{FacilityServiceTest,FacilityControllerTest,FacilityPostgresTest,LegacyFacilityBoundaryTest}.java`
- `arena-login/src/test/java/com/arena/login/controller/FacilityProxyTest.java`
- `arena-ui/src/app/facilities/facility-api.service.ts`
- `arena-ui/src/app/facilities/facility-resources.component.{ts,html,scss}`
- `arena-ui/scripts/test-facilities.mjs`
- This report.

Modified:

- Core `AcademyPlayArea`, its repository, `AcademyFacilityService`, `AcademySetupService`, setup tests, legacy persistence fixture and database-startup tests, `AuditEventRepository`, `ErrorCode`, `ApiErrors`, `GlobalExceptionHandler`, master changelog.
- Login `TokenController`, `ErrorCode`, `ErrorResponse`: recognize facility errors and preserve whitelisted reason/conflict details through the gateway without forwarding arbitrary upstream descriptions/fields.
- UI routes/sidebar, Sport Configuration contextual support/link, `api-error.ts`, package test command and the existing navigation regression assertion.
- Previous preview report is marked superseded.

Removed only obsolete preview implementation: `facility-preview.component.{ts,html,scss}`, `facility-preview.store.ts` and its mock-only test script. The source report is retained for provenance.

## Automated validation

- Full non-disruptive Core regression command completed successfully. Environment-gated PostgreSQL/Keycloak suites were explicitly disabled for this execution.
- Final focused Core/setup run: **27 passed**, zero failures/errors; **33 skipped** (9 new PostgreSQL cases, 22 existing training persistence cases, 2 schema-startup cases).
- Login full suite: **20 passed**, zero failures/errors, including idempotency-header forwarding and safe structured conflict propagation.
- UI full suite: **104 passed**, zero failures, including dynamic sports, response-driven generation, per-facility PATCH/versioning, uncertain retries, draft protection and field errors.
- Final Angular production build: **passed**.
- Changelog 06 XML parsed; `git diff --check` passed. No PostgreSQL migration or locking SQL was executed.
- New PostgreSQL cases cover persisted IDs/codes/null pricing, original-result replay, changed-payload rejection, concurrent same/different keys, multi-type allocation, counter/audit rollback, whole-PATCH rollback, null/version semantics, legacy high-water marks and tenant/audit isolation. These are compiled tests, not verified database results.
- The legacy schema-upgrade fixture now excludes all new feature migrations from its historical baseline and checks the new ledger tables after upgrade. Its existing stale draft-batch dependency assertion elsewhere in the opt-in training suite remains a previously identified test-alignment issue, outside this facility change.
- No real browser/device, external email, payment or session tests were run. Session race and snapshot tests are deferred along with session integration.

Commands used: `mvn -f arena-core/pom.xml test` with external-test URLs explicitly blank; final selection `FacilityServiceTest,FacilityControllerTest,FacilityPostgresTest,LegacyFacilityBoundaryTest,AcademySetupTest,TrainingPersistenceTest,DatabaseSchemaStartupTest`; `mvn -f arena-login/pom.xml test`; `npm --prefix arena-ui test`; `npm --prefix arena-ui run build`.

## Deployment and manual verification

No migration was executed here. Deploy/apply changelog 06 through the usual Liquibase process after reviewing legacy rows and taking the normal backup. Earlier feature migrations remain prerequisites. No destructive conversion or cleanup is included.

The updated artifacts requiring user-managed deployment/restart are **Arena Core, Arena Login and Arena UI**. No Keycloak change/restart or PostgreSQL restart is required by this code change.

PostgreSQL tests are opt-in only, require an already provisioned disposable local database, and do not start containers themselves. Their URL must match `jdbc:postgresql://127.0.0.1:<port>/arena_test_facilities_<suffix>`; credentials are the isolated test fixture values, schema `arena_test`. Never point them at development/SIT/production. After separately provisioning the approved disposable DB, run the targeted FacilityPostgresTest with `ARENA_FACILITY_TEST_DB_URL` set appropriately. The test suite applies real Liquibase migrations to that disposable DB.

Runtime checks still needed: actual PostgreSQL migration/rollback/locking/idempotency, fresh and repeated deployment, proxy PATCH forwarding, lost-response recovery across reload, two-user concurrent changes, dynamic tabs/disabled capabilities, mobile/keyboard rendering and legacy type-mapping review. Running Sessions tests remain deferred by user instruction.
