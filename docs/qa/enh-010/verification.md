# ENH-010 — Add Tables UX

## Changes

Inspected the existing `FacilityResourcesComponent` generator, independent-rate forms, create/idempotency flow, versioned bulk PATCH and sticky Save Configuration workflow before editing.

- When saved facilities exist, Add New Tables starts collapsed; Existing Tables is immediately visible.
- With no facilities, the same generator opens for onboarding, retaining game type, count, hourly and optional frame-rate fields.
- A `+ Add Tables` action beside Existing Tables opens the existing form above the list. The corresponding existing court workflow uses the same behavior.
- Scrolling runs only when the expanded form falls outside the viewport.
- The summary has an explicit Collapse label. Collapse retains drafts; Cancel adding tables uses a shared confirmation before discarding only new-facility drafts. Existing table edits remain intact. Uncertain/pending generation cannot be discarded through this action.
- Successful configuration saves update the list from canonical API-returned records and collapse the generator. Reload retrieves the saved list through the existing GET. Standard shared success notifications are used, including saves that do not require a follow-up rate PATCH.
- Invalid generator fields reopen the form without creating facilities.
- Existing editing, Bulk Edit Rates, versions, immutable IDs, idempotency/retry logic and the single sticky Save Configuration action are retained. No new Save action or backend/API/database change was added for ENH-010.

## Files modified

- `arena-ui/src/app/facilities/facility-resources.component.ts`
- `arena-ui/src/app/facilities/facility-resources.component.html`
- `arena-ui/src/app/facilities/facility-resources.component.scss` (targeted heading/action alignment only)
- `arena-ui/scripts/test-facilities.mjs`
- New `arena-ui/e2e/specs/add-tables.spec.ts`
- `arena-ui/e2e/specs/dashboard.spec.ts`: stale regression selectors updated for current type-label casing, frozen-bill value, attention link, settlement input and mobile sidebar. No application/session behavior changed to satisfy these tests.

## Verification

Final executed results are recorded in `results.json`. New browser cases cover zero/existing facilities, immediate list visibility, expand/collapse/draft preservation, Cancel/confirmation, preservation of existing edits, invalid pricing without requests, creating multiple types, save notification/collapse, reload and unchanged original IDs/rates. Both desktop light and tablet dark projects run these cases. Unit coverage also checks invalid pricing, cancel preservation, generated-only pricing and idempotent retries.

Facility/session card layouts, pricing and billing rules, authentication and permissions remain unchanged. Browser tests use synthetic API fixtures; no running shared services or real club data were changed.

## Final combined execution (2026-11-14)

- Angular production build, Playwright TypeScript check and `git diff --check`: passed.
- Frontend component/design/validation tests: **204 passed**, zero failures.
- Core/BFF check-in and host tests: **81 passed**; API documentation/security tests: **6 passed**; schema/source manifests validated.
- Full 172-case browser regression: **167 passed**, five failed on stale test selectors/expectations (frozen bill container vs value, renamed payment field and the mobile menu accessible name). Test-only corrections were made; no session code was changed.
- Focused final rerun: **24 passed**, zero failures, covering every QR/Add Tables case and all three affected dashboard scenarios in both projects. All **172 distinct browser cases** passed across the final regression and focused rerun.
- Screenshots were captured and inspected in desktop light and tablet dark. Existing component styles/colors were retained.
