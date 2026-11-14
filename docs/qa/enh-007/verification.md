# ENH-007 — First-time facility setup navigation

Verified 2026-11-14.

## Implementation

- Reuses GET `/api/v1/academies/current/facilities/context`, the registered sport `active` and `sessions` fields, and existing `/academy/sports` routing and permission guards.
- No sports: “No sports registered yet”, the requested description and Add Sport action.
- Selected sport inactive, facility access disabled, or both: precise explanation and Configure Sport action. Unsupported-only registrations retain explicit support guidance.
- Defaults to the first active, facility-enabled supported sport. An explicit supported `sportId` and manual sport selection remain available for viewing historical configuration, including disabled sports.
- Existing facilities and safe deactivation remain available. The creation form and all existing configuration behavior remain unchanged for eligible sports.
- No API, backend, database, billing, session, permission, or CSS changes.

## Files changed for this enhancement

- `arena-ui/src/app/facilities/facility-resources.component.ts`
- `arena-ui/src/app/facilities/facility-resources.component.html`
- `arena-ui/scripts/test-facilities.mjs`
- `arena-ui/e2e/specs/facility-setup.spec.ts`
- This report and `results.json`.

## Executed verification

- `npm --prefix arena-ui test`: 190 passed, 0 failed.
- `npm --prefix arena-ui run build`: passed.
- `npm --prefix arena-ui run e2e:typecheck`: passed.
- Playwright `facility-setup.spec.ts` and existing `empty-state.spec.ts`: 44 passed, 0 failed, across desktop light (1440×1000) and tablet dark (820×1180).
- `git diff --check`: passed.

Browser coverage includes both Dashboard → Add Facility and direct facility navigation for no sports, inactive sport, disabled access, both disabled conditions, unsupported-only sports and eligible sport. It also verifies eligible default selection, explicit inactive sport selection, historical rows and safe deactivation, and switching back to eligible setup. Existing dashboard cases cover unavailable, occupied, inactive and unconfigured facilities, failed API responses and role-based setup actions.

Browser tests use synthetic intercepted API responses and an isolated static server; no live tenant records were modified. Backend tests were not rerun because backend code and contracts were unchanged. No regressions were found in these checks.
