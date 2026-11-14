# Sports & Activities — business rules and facility preservation

Verified 2026-11-14 using isolated data.

## Audit

| Area | Existing behavior | Change |
| --- | --- | --- |
| Business classification | Onboarding normalizes the customer plan (`SINGLE`, `DUAL`, `MULTI_SPORTS`, `ACADEMY`) into `CustomerEntity.sports`; organization type and public Business Profile labels are separate. | Enforce the single-active-sport limit for persisted `SINGLE` plans. Do not infer plan limits from the public name, business-type label or selected sport name. |
| Transaction/concurrency | `AcademyAuthorization.actor/manage` and `TrainingService.sportsScope` acquire the customer pessimistic write lock. Sport mutations are transactional. Session starts also use the same tenant lock, followed by the facility lock. | Reuse this lock for count validation and registration/reactivation. No new locking infrastructure. |
| Registration | Single and bulk registration create active activities; bulk validation and writes already roll back atomically. No single-sport count check existed. | Validate before writes. Add optional `active=false` for inactive registration; omitted/null retains active registration. Reactivation checks the same rule. No automatic sport deactivation. |
| Facility configuration | Parent sport deactivation does not delete facilities; management lists include inactive sports. Capability-disabled management previously allowed safe facility deactivation. | Inactive parent sports now block every facility mutation, including individual activation/deactivation. Active sports with only facility access disabled retain their existing safe-deactivation behavior. Protect the legacy generic facility endpoint from moving a facility away from an inactive parent. |
| Operational safety | Session start/reserved-start eligibility rejects inactive sports. Dashboard availability already checks parent active/capability state. Paused sessions retain `STARTED` status and use `pausedAt`. Deactivation checks `STARTED` and `RESERVED`, plus training dependencies. | Preserve those checks and return specific running/paused-session and reservation errors rather than a training-only error. |
| History/finance | Facilities, sessions, bills and payments are not deleted when sport status changes. | Preserve this behavior and verify stored identifiers, tariffs, individual statuses, unsettled amounts and payments. No billing changes. |

## Resulting behavior

- A `SINGLE` club can register one active activity, add further inactive activities, deactivate safely and activate a different activity. Initial, subsequent, bulk and direct API registrations use the same validation. Concurrent registrations/reactivations cannot create two active sports.
- `MULTI_SPORTS` and academy plans retain multiple active activities. No additional restriction was introduced for other plans.
- `SINGLE_SPORT_LIMIT_EXCEEDED` returns HTTP 409 with: “Your club allows only one active sport. Deactivate your current sport before activating another.”
- `SPORT_SESSION_ACTIVE` and `SPORT_RESERVATION_EXISTS` return HTTP 409 with actionable messages. Core, BFF and Angular reuse their existing standard error handling and safe message allowlists.
- Unresolved reservations remain blockers under the existing conservative policy, including stale records still marked `RESERVED`; nothing is silently cancelled.
- The activity catalogue adds “Register selected activities as inactive”. Failed registration retains the modal, selected catalogue entries and custom names.
- Inactive facilities remain visible with an Inactive Sport indicator, read-only explanation, names, codes, rates and original individual statuses. Edit/status/save actions are hidden. Reactivating the parent restores existing controls without recreating facilities or changing individual status.
- If both parent status and facility access are disabled, guidance explains both requirements.
- No CSS, facility-card layout, pricing, billing formula, authentication/permission rules, database schema or migration changes.

## Modified files

Core:
- `arena-core/src/main/java/com/arena/core/training/TrainingService.java`
- `arena-core/src/main/java/com/arena/core/training/TrainingRequests.java`
- `arena-core/src/main/java/com/arena/core/training/TrainingController.java`
- `arena-core/src/main/java/com/arena/core/training/AcademyFacilityService.java`
- `arena-core/src/main/java/com/arena/core/facility/FacilityService.java`
- `arena-core/src/main/java/com/arena/core/exception/ErrorCode.java`

BFF:
- `arena-login/src/main/java/com/arena/login/exception/ErrorCode.java`

Angular:
- `arena-ui/src/app/api-error.ts`
- `arena-ui/src/app/training/sports-activities.component.ts`
- `arena-ui/src/app/training/sports-activities.component.html`
- `arena-ui/src/app/training/sport-configuration.component.ts`
- `arena-ui/src/app/facilities/facility-resources.component.ts`
- `arena-ui/src/app/facilities/facility-resources.component.html`

Tests:
- `arena-core/src/test/java/com/arena/core/training/ActivityRegistrationTransactionTest.java`
- `arena-core/src/test/java/com/arena/core/training/SportFacilityPreservationTest.java`
- `arena-core/src/test/java/com/arena/core/facility/FacilityServiceTest.java`
- `arena-core/src/test/java/com/arena/core/facility/LegacyFacilityBoundaryTest.java`
- `arena-core/src/test/java/com/arena/core/sessions/RunningSessionServiceTest.java`
- `arena-login/src/test/java/com/arena/login/controller/FacilityProxyTest.java`
- `arena-ui/scripts/test-sports-activities.mjs`
- `arena-ui/scripts/test-facilities.mjs`
- `arena-ui/e2e/specs/sport-business-rules.spec.ts`
- `arena-ui/e2e/specs/facility-setup.spec.ts` — updates previous safe-deactivation expectations for the stronger inactive-parent requirement.

Generated API documentation:
- `docs/api/arenaops-core-openapi.yaml`
- `docs/api/arenaops-login-openapi.yaml`
- `docs/api/core-source-review.json`
- `docs/api/login-source-review.json`

This report and `results.json` record verification.

## Executed tests

- Core: **82 passed**, zero failures/errors, using `SportLifecycleTest`, `ActivityRegistrationTransactionTest`, `SportFacilityPreservationTest`, `FacilityServiceTest`, `FacilityControllerTest`, `LegacyFacilityBoundaryTest`, `RunningSessionServiceTest`, `ManagerAuthorizationTest`.
- BFF: **4 passed**, using `FacilityProxyTest`, `SessionCheckoutProxyTest`. New error codes survive BFF sanitization without forwarding internal descriptions.
- OpenAPI contract/documentation security: **6 passed** across both modules; exported contracts, examples, schemas and source-review validation passed.
- `npm --prefix arena-ui test`: **194 passed**.
- `npm --prefix arena-ui run build`: passed.
- `npm --prefix arena-ui run e2e:typecheck`: passed.
- Playwright: **50 passed**, zero failures, from `sport-business-rules.spec.ts`, `facility-setup.spec.ts`, `empty-state.spec.ts`, across desktop light (1440×1000) and tablet dark (820×1180).
- `git diff --check`: passed.

Concurrency tests run simultaneous different registrations and simultaneous reactivations against real transactional H2 persistence with the existing pessimistic customer lock. Integration tests also cover bulk rollback, mixed active/inactive registration, switching, direct HTTP validation/authorization, safe deactivation, read-only API rejection, restoration and persisted financial history. Browser checks cover preserved rejected selections, inactive registration, meaningful operation errors, direct navigation and restoration of facility controls.

## Limits

- Concurrency and persistence checks use isolated H2 in PostgreSQL compatibility mode. They were not run against shared SIT/production or a PostgreSQL container.
- Playwright uses synthetic intercepted API responses; backend transactions and HTTP/BFF contracts are tested separately. No live tenant records were modified.
- Existing inconsistent tenant data is not automatically repaired or deactivated. Legacy records whose plan field contains a sport name rather than a canonical onboarding plan are not guessed to be `SINGLE`; their authoritative plan needs administrative verification if a limit should apply.
- No regressions were found in the executed checks. No implementation blocker remains.
