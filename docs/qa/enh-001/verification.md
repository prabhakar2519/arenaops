# ENH-001 — Dashboard empty state

Implemented and verified on 2026-11-14.

When the Available Facilities view has an empty facility list from the existing dashboard API, it displays:

- **No facilities configured yet**
- Add your first snooker table to start managing sessions and billing.
- **Add Facility**, linking to `/academy/facilities`, for Owner/Manager roles with existing management access.

Staff see the guidance without an unauthorized action. No new permissions or API calls are introduced. Existing facility rows that are occupied, inactive or incomplete retain the existing “No facilities available to start” guidance. An API failure retains its error state and does not imply an empty academy.

The change reuses the existing empty container, global button styles, RouterLink and `DashboardInformationService.canManage`. No facility-card CSS, session/billing logic, backend contract or database change.

Files: `arena-ui/src/app/sessions/running-dashboard.component.html`; `arena-ui/e2e/specs/empty-state.spec.ts`; the existing test fixture adds facility-context/list responses so navigation is exercised through the actual module.

Verification:

- 18/18 Playwright checks passed: 9 scenarios in desktop/light and tablet/dark projects, using the real production build with synthetic API responses.
- Covers Owner, Full Operations Manager, Standard Manager, Staff; exact copy/action; navigation into Manage Tables & Rates; occupied/inactive/incomplete/available facilities; failed dashboard response.
- 187/187 existing frontend tests passed.
- Angular production build, E2E TypeScript check and `git diff --check` passed.
- An initial outage test helper wrongly expected the normal heading on a failed API response; corrected the test setup and reran all18 checks successfully.

HTML report: `arena-ui/playwright-report/index.html`. Machine-readable full run: `arena-ui/test-results/results.json` (generated/ignored). Aggregate evidence is retained in this directory's `results.json`.

Re-run: build the UI, then `npm --prefix arena-ui run e2e -- e2e/specs/empty-state.spec.ts`. Playwright requires its Chromium install, or an existing compatible executable via `PLAYWRIGHT_CHROMIUM_EXECUTABLE`.
