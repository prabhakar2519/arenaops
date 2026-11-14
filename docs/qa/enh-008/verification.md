# ENH-008 — Global dialogs and notifications

Verified 2026-11-14 before starting ENH-009.

## Audit and implementation

Eight native confirmation calls were found: Business Profile discard (1), activity configuration discard (1), Facilities price save/deactivation/latest-draft/discard (4), and student enrollment/status changes (2). All eight were replaced. No native alerts or prompts were found. Staff, Dashboard, session management, manual reservations, check-ins, billing, subscription, administration and onboarding were audited; their existing feature forms and inline validation remain intact.

No Angular Material, CDK Overlay or PrimeNG dependency was installed. Existing drawers, `ao-confirm`, theme tokens, Inter and the safe `apiError`/`ApiNotifications` pipeline were reused. One root `UiFeedbackService` and host provide queued confirmations, acknowledgement dialogs and bounded, deduplicated success/error/warning/information toasts. `ao-confirm` now adapts to the same dialog panel. Existing complex forms retain their own workflow.

The themed DOM `<dialog>` component uses the browser top layer for background blocking; this is an ArenaOps-rendered element, not `window.confirm`. It traps focus, restores focus, blocks scrolling, supports keyboard cancellation of noncritical dialogs, and prevents cancellation or repeated submission during an operation. Destructive dialogs require explicit Cancel. If the original trigger is removed or disabled after success, focus falls back to the main content region.

Facility saves retain the existing PATCH payload, versions, field errors, price snapshots and unknown-result reconciliation. Cancel makes no request. Saving remains visible until completion; safe failures stay in the dialog for explicit retry. Unknown-result/stale-conflict retries require the existing reconciliation flow. Success closes the dialog and emits one toast. Inline validation remains where it helps correct a form. Converted success messages use toasts without duplicated inline success banners. Error messages use allowlisted API descriptions, not raw backend diagnostics.

Toasts show at most three messages; ordinary successes auto-dismiss after five seconds, information after seven seconds. Errors/warnings have no timer. Hover/focus pauses dismissal. Optional actions and manual dismissal are supported. Matching messages coalesce; resolved dialog errors are cleared on successful retry.

## Files

Added:
- `arena-ui/src/app/ui/ui-feedback.service.ts`
- `arena-ui/src/app/ui/ui-feedback.component.ts` and `.scss`
- `arena-ui/src/app/ui/dialog-panel.component.ts`, `.html`, `.scss`
- `arena-ui/scripts/feedback-fixture.mjs`
- `arena-ui/scripts/test-feedback.mjs`
- `arena-ui/e2e/specs/ui-feedback.spec.ts`

Updated:
- `arena-ui/src/app/app.ts`, `app.html`, `app.scss`
- `arena-ui/src/app/ui/shared.ts`
- Business Profile component TS/HTML
- Sports & Activities component TS/HTML and sport configuration component TS
- Facilities & Resources component TS/HTML
- Students component TS/HTML
- Component test loaders/assertions in `test-business-profile.mjs`, `test-facilities.mjs`, `test-sports-activities.mjs`, `test-training.mjs`, `test-session-history.mjs`
- `arena-ui/package.json` test command

## Executed verification

- Frontend: **202 tests passed**, zero failures.
- Angular build and Playwright TypeScript check passed.
- **66 Playwright tests passed**, across desktop light and tablet dark, including existing setup, sport rule and profile validation regression checks.
- Two additional focused appearance checks passed: actual Inter rendering, primary colors, >=4.5:1 dialog body/button text contrast, viewport bounds, focus and screenshots.
- Native-call AST audit and `git diff --check` passed.
- Screenshots inspected: [light](dialog-light.png), [dark](dialog-dark.png).

Browser tests use isolated synthetic API responses. No backend, database, billing, API or production-service changes were made for ENH-008.

## Remaining browser-controlled behavior

Three `beforeunload` protections remain for unsaved Business Profile, activity and facility edits. Browsers control warnings when closing/reloading a tab and do not permit asynchronous application dialogs there. In-app route changes now use the shared themed dialog. The operating-system print dialog is also retained where printing is requested. There are no remaining application calls to native JavaScript alert/confirm/prompt.
