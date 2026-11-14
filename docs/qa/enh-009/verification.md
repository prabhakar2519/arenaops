# ENH-009 — Facility QR Code UI

## Implementation

Facilities & Resources now has **Tables & Rates** and **Facility QR Codes** tabs. The existing configuration form remains mounted when switching tabs, preserving quantities, independent rates, editing state, versions and unsaved drafts. QR content is loaded lazily and cached while switching tabs.

The QR tab has a responsive three/two/one-column grid using existing ArenaOps Inter and theme tokens. Each facility has its own code/display name, table type, generated PNG preview, Download QR and Print QR actions. Inactive facilities, unconfigured rates, disabled sports and unavailable/revoked codes have explanatory states. The grid never invokes regeneration or revocation automatically. Owner-only management remains on the existing QR management route and uses shared application confirmations/toasts.

The existing `POST /api/academy/facilities/{id}/check-in-qr` read-or-create operation is reused. Existing permanent public IDs are retained. Download uses the generated PNG; Print renders only the selected facility in an isolated iframe before invoking the browser/OS print dialog. Printing does not navigate away or require popup permissions.

## Website independence and security

The dependency was in `PermanentCheckinService`: issuing a URL used `PublicClubService.origin`, and public token resolution used `resolve`; both required a published website. They now use dedicated check-in venue/origin lookup methods. Website publication, management and reservation feature flags are unchanged.

When subdomain hosting is not ready (the existing default), the backend returns `/check-in#<permanent-id>`. Angular resolves it against the current browser origin:

- LAN: `http://192.168.0.219:4200/check-in#<permanent-id>`
- Localhost: `http://localhost:4200/check-in#<permanent-id>`
- SIT: `https://sit.arenaops.in/check-in#<permanent-id>`
- Shared production host: `https://arenaops.in/check-in#<permanent-id>`

When existing `PUBLIC_CLUB_HOSTING_READY` is enabled and the club has a configured slug, its URL remains `https://<slug>.arenaops.in/check-in#<permanent-id>`, independent of website publication. Existing local/SIT slug preview URLs remain supported. Changing host selection does not rotate QR IDs; previously printed tenant URLs still require their DNS/TLS infrastructure.

The BFF accepts shared-host access only on the two public POST check-in routes. It signs trusted canonical hosts; local/private hosts are DEV-only, and the SIT host is SIT-only. Core verifies the signature/time before resolving the facility's academy from the unguessable permanent identifier. Tenant-host/slug requests additionally require the token's academy to match the slug. Arbitrary academy IDs are not accepted. Root shared-host resolution is not allowed for website endpoints.

Existing tenant/operator authorization, active venue/sport/facility eligibility, revocation, nonce hashing/single use/expiry, rate limits, idempotency, pending expiry and atomic staff confirmation remain in place. Public endpoints return no customer records or financial data and do not use staff cookies/tokens. No schema change or migration was needed. No pricing, billing, session or reservation calculation was changed.

## Modified files for ENH-009

Frontend:
- `arena-ui/src/app/facilities/facility-resources.component.ts`, `.html`, `.scss`
- New `arena-ui/src/app/checkin/facility-qr-grid.component.ts`, `.html`, `.scss`
- New `arena-ui/src/app/checkin/facility-qr.utils.ts`
- `arena-ui/src/app/checkin/facility-qr.component.ts`
- `arena-ui/src/app/sessions/running-dashboard.component.ts` (delegates existing display-URL conversion to shared helper)
- `arena-ui/scripts/test-facilities.mjs`, `test-session-checkout.mjs` (test import loaders)
- New `arena-ui/e2e/specs/facility-qr-grid.spec.ts`

Backend:
- `arena-core/src/main/java/com/arena/core/checkin/PermanentCheckinService.java`
- `arena-core/src/main/java/com/arena/core/checkin/FacilityCheckinController.java`
- `arena-core/src/main/java/com/arena/core/publicclub/PublicClubService.java`
- `arena-core/src/main/java/com/arena/core/publicclub/PublicClubHost.java`
- `arena-login/src/main/java/com/arena/login/service/PublicClubProxyHost.java`
- `arena-core/src/test/java/com/arena/core/sessions/PublicClubsIntegrationTest.java`
- `arena-core/src/test/java/com/arena/core/publicclub/PublicHostContractTest.java`
- `arena-login/src/test/java/com/arena/login/controller/PublicCheckinProxyTest.java`
- `docs/api/arenaops-core-openapi.yaml`, `arenaops-login-openapi.yaml`, `core-source-review.json` and `login-source-review.json`.

## Verification

See `results.json` and the ENH-010 report for final frontend regression results. The ten dedicated QR browser cases passed in desktop light and tablet dark, including decoded QR contents/facility association, unchanged IDs across tab switches, rate-draft preservation, unavailable states, download filename/PNG, selected-facility printing, keyboard/mobile behavior and anonymous form access without fetching a website.

Backend execution: 67 Core tests (PublicClubsIntegrationTest 45, FacilityCheckinIntegrationTest 15, PublicHostContractTest 5, PublicCheckinRateLimitTest 2) and 14 BFF PublicCheckinProxyTest tests passed. OpenAPI/security export verification added six passing tests; both API manifests validated. Tests cover unpublished/missing website configuration, website-management disabled, unchanged IDs, shared and tenant hosts, cross-tenant/revoked/disabled codes, signature failures, external-host spoofing and existing check-in/session safety.

No live PROD/SIT services, DNS, TLS, database records or deployment flags were modified. Tests use synthetic browser responses and isolated backend fixtures. Shared public routing requires the existing matching `PUBLIC_CLUB_HOST_KEY` signing configuration in Core/BFF. Tenant subdomain URLs require provisioned DNS/TLS and the hosting-ready flag. Browser tests verify invocation/content of printing, not physical printer output.

## Final combined execution (2026-11-14)

- Angular production build, Playwright TypeScript check and `git diff --check`: passed.
- Frontend component/design/validation tests: **204 passed**, zero failures.
- Core/BFF check-in and host tests: **81 passed**; API documentation/security tests: **6 passed**; schema/source manifests validated.
- Full 172-case browser regression: **167 passed**, five failed on stale test selectors/expectations (frozen bill container vs value, renamed payment field and the mobile menu accessible name). Test-only corrections were made; no session code was changed.
- Focused final rerun: **24 passed**, zero failures, covering every QR/Add Tables case and all three affected dashboard scenarios in both projects. All **172 distinct browser cases** passed across the final regression and focused rerun.
- Screenshots were captured and inspected in desktop light and tablet dark. Existing component styles/colors were retained.
