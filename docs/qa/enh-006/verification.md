# ENH-006 — Hide Public Club Website management for MVP

Implemented and verified 2026-11-14.

## Behavior

The entire Public club website card and Manage public website action are hidden from Business Profile using the existing MVP switch structure. Both `/academy/public-site` and `/academy/public-site/preview` redirect authenticated users to Dashboard while disabled. QR error views no longer expose links to the hidden settings page.

Core enforces `arena.public.website-management-enabled`, defaultfalse, before any management read/write or Owner preview work. These operations return safe RESOURCE_NOT_FOUND/404 while disabled, including for an Owner:

- GET `/api/academy/public-site`
- PUT `/api/academy/public-site`
- GET `/api/academy/public-site/preview`
- GET `/api/academy/public-site/preview/logo`

Authentication can reject an unauthorized request earlier. Existing Owner authorization still applies if the feature is enabled later. The server flag is authoritative; changing browser code cannot bypass it.

All implementation, database structures, stored publication/slug settings, logo data and deployment configuration remain. No migration or data deletion. Business Profile read/edit, phone/email/address fields, logo upload and existing customer/session functionality are unchanged.

Scope: this disables website **management and Owner preview**, not existing anonymous published-site projections or the host/slug lookup used by QR check-in. Configured clubs' QR flows continue to work. A club without a published setup already could not issue a QR; this prerequisite is unchanged, and MVP users cannot use the now-hidden website manager to configure it. No new provisioning/automatic publication behavior was introduced.

## Switches

- Frontend: `arena-ui/src/app/features.ts`, `MVP_FEATURES.publicClubWebsite=false`.
- Backend: `arena.public.website-management-enabled=false` (default in @Value).
- Future release: explicitly enable both, rebuild Angular/restart Core, and rerun the retained feature tests. Public reservation requests have a separate switch and remain disabled for MVP.

## Application files changed

- `arena-ui/src/app/features.ts`
- `arena-ui/src/app/business-profile/business-profile.component.ts`
- `arena-ui/src/app/business-profile/business-profile.component.html`
- `arena-ui/src/app/app.routes.ts`
- `arena-ui/src/app/sessions/running-dashboard.component.ts`
- `arena-ui/src/app/sessions/running-dashboard.component.html`
- `arena-ui/src/app/checkin/facility-qr.component.ts`
- `arena-core/src/main/java/com/arena/core/publicclub/PublicClubService.java`

Tests/verification files: PublicClubsIntegrationTest; frontend test loaders in test-business-profile.mjs/test-public-clubs.mjs/test-session-checkout.mjs; new Playwright mvp-public-website.spec.ts; updated mvp-reservations.spec.ts; API source-review manifests; this report and results.json. Retained feature tests explicitly opt into enabled mode so future-use behavior remains covered.

## Executed verification

- `npm --prefix arena-ui test`:188 passed,0 failed.
- Core targeted suite (PublicClubsIntegrationTest, BusinessProfileTest, BusinessProfileTransactionTest, ContactPhoneValidationTest):67 passed,0 failed.
- Playwright mvp-public-website.spec.ts, business-profile-phone.spec.ts and mvp-reservations.spec.ts:18 passed in desktop/light and tablet/dark projects. Covers hidden card/action, blocked direct management/preview routes with no settings API fetch, unchanged edit/contact/logo controls, phone-save regression, retained reservation gating and hidden QR setup links.
- Angular production build, E2E TypeScript check, OpenAPI/source-review validation and git diff --check passed.
- Core tests verify disabled default, all four HTTP operations return404, stored publication/slug/version unchanged, and existing published club/QR inspection still works. Existing Business Profile transaction tests exercise logo/profile behavior.
- Initial Core test compilation could not resolve existing AcademyBilling classes; the files were present. Retrying the same targeted command passed without changes to billing sources.

Browser checks use isolated synthetic API fixtures; Core tests use H2 and mocked authorization identities. No live production/SIT data, DNS/TLS or running services were modified. Generated HTML/JSON/trace files remain under ignored arena-ui/playwright-report and arena-ui/test-results. Aggregate evidence is retained here.
