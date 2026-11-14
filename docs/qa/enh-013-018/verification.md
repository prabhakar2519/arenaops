# ENH-013–018 implementation and verification

Date: 2026-11-14. Scope: consolidated attachment `18d85fbf-919a-410a-904b-3f171cd736b6`.

## Audit and reused architecture

- Reused the Angular dashboard, existing six operational tabs, 30-second floor refresh, independent check-in polling, session controls and customer management route. No operational pagination was introduced. Recent sessions retain the existing limit of 20.
- Reused Core's academy authorization, active membership/assigned-sport checks, Owner/FULL_OPERATIONS financial policy, existing session history specification and PostgreSQL session/visit/customer records. No Keycloak roles, grants or provisioning flows changed.
- Reused snapshotted hourly rates, billable/paused seconds, frozen stop quotes, settlement and discount records. No pricing formulas, payment writes or historical financial records changed.
- Reused permanent QR records, 256-bit random public identifiers, revocation/rotation, nonce hashes/expiry, request idempotency, rate limits, tenant/facility association and atomic staff confirmation. Public identifiers already exist as stable printable handles; this change introduces no token storage. A permanent QR is public, not a bearer authorization to start a session. Submission still requires the existing short-lived single-use form nonce and staff approval.
- Gaps found: tall/decorative cards, missing selected-tab search, header-only Customers link, no operational contact projection, operator-based detailed history visibility, no history XLSX action, and QR destinations depending on website-hosting readiness/client host rewriting.

## Implemented changes

| Enhancement | Result |
| --- | --- |
| ENH-013 | Compact responsive floor grids; removed decorative billiard icons/descriptions on available cards. Retained rates, reservation notes and Start/Reserve/Show QR. Active cards retain state, customer, playing time, planned end, charges, start time and all controls. Touch actions are at least 44px high. Facility name/code search and game-type filtering apply to the selected tab, including ledger and check-in tabs; tab counts remain unfiltered. |
| ENH-014 | Customers moved below the Dashboard group and above Setup & Management with a users icon and existing active styling. Owner/Manager visibility and the existing guarded route remain unchanged. |
| ENH-015 | Added masked `playerMobile` to the session projection for active sessions. Uses the existing academy-scoped customer visit and non-erased customer record; only the last four digits are returned. No new phone column or duplicate customer data. Supports both details, name only, phone only and “Walk-in customer.” Closed history/export do not load contact details. Existing manual-start validation is preserved. |
| ENH-016 | Added backend XLSX export and Session History Download Excel. Apache POI produces genuine OOXML, all matching pages, typed local dates, numeric elapsed/paused durations and numeric currency fields, styled headings/widths and frozen header. Empty results produce a valid header-only workbook. Customer strings are string cells, not formulas. |
| ENH-017 | History projection no longer grants detailed money solely because the user started/stopped the session. Owner/FULL_OPERATIONS retain full financial columns. Standard Manager/Staff and other non-financial permitted operators receive Original Bill and coarse payment state, with other financial fields null. Angular and XLSX present Paid/Unpaid/Not Finalized; partial payment is Unpaid. Aggregate/report access remains governed by existing financial authority. |
| ENH-018 | Newly displayed QR links use Core's configured shared application origin and existing `/check-in#<public-id>` route. They do not use the tenant's website/subdomain or request Host. Angular preserves absolute server destinations instead of rewriting them to a viewing host. Existing relative DEV links remain readable for compatibility. Viewing/downloading does not rotate identities. Existing tenant-alias check-in routes continue validating token/tenant matching. |

## API and schema

- Existing history: `GET /api/academy/running-sessions/history`, same filters/pagination. For non-financial actors only `originalAmount` and `paymentStatus` remain from historical financial fields; detailed amounts/discount metadata remain null and `financialVisible=false`.
- New: `GET /api/academy/running-sessions/history/export?from=YYYY-MM-DD&to=YYYY-MM-DD&status=...&search=...`.
- MIME: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`; attachment filename `arenaops-session-history-YYYY-MM-DD.xlsx`; `Cache-Control: no-store`. BFF's existing authenticated byte proxy forwards the workbook without transformation.
- Same academy/date/status/player/sport-authorization specification as the history API, with a repeatable-read export transaction and all matching pages. Detailed columns depend on backend financial authority, never client flags.
- Session response adds optional masked `playerMobile`. Public QR/check-in contracts and session mutation contracts are unchanged.
- **No Liquibase migration or database writes/backfill are required.** Existing visit `sessionId`, customer mobile, session `originalAmount`, payment status and pause totals are reused.

## Environment configuration

Core's new `arena.public.application-url` is set using **`ARENAOPS_PUBLIC_APP_URL`**, falling back to the existing `ARENAOPS_FRONTEND_URL`, then `APP_BASE_URL`. An origin must contain only scheme, hostname and optional port; no credentials, query, path or fragment. Deployed origins require HTTPS and reject localhost/IP literals. No request Host/forwarded Host is used for generation.

| Environment | Core runtime settings / resulting prefix |
| --- | --- |
| Local PC only | `ARENA_ENV=dev`, `ARENAOPS_PUBLIC_APP_URL=http://localhost:4200` |
| LAN/mobile | `ARENA_ENV=dev`, `ARENAOPS_PUBLIC_APP_URL=http://192.168.0.219:4200` |
| SIT | `ARENA_ENV=sit`, `ARENAOPS_PUBLIC_APP_URL=https://sit.arenaops.in` |
| PROD | `ARENA_ENV=prod`, `ARENAOPS_PUBLIC_APP_URL=https://arenaops.in` |

When no origin/fallback is configured, recognized environments default to localhost:4200 for DEV, sit.arenaops.in for SIT and arenaops.in for PROD. Unknown environment without a configured origin fails closed. Explicit LAN configuration is required for phone testing; a phone cannot reach the development machine using `localhost`.

Reuse matching `PUBLIC_CLUB_DOMAIN` and `PUBLIC_CLUB_HOST_KEY` in Core/Login; `ARENA_ENV` must be set appropriately in both. Existing BFF host validation/signing still applies. Website management/publication flags are irrelevant to check-in. No running service, DNS, certificate or Caddy configuration was changed. Tenant subdomains are not introduced; previously printed valid identities are retained.

## Files changed for this request

Frontend, relative to `arena-ui/`:

- `src/app/sessions/running-dashboard.component.{ts,html,scss}` — density, search/filter, customer display.
- `src/app/sidebar/sidebar.component.ts` — Customers navigation.
- `src/app/sessions/running-sessions.service.ts` — masked contact type and binary export.
- `src/app/sessions/session-history.component.{ts,html}` — role columns and XLSX action.
- `src/app/checkin/facility-qr.utils.ts` — authoritative configured absolute destinations.
- `scripts/test-session-checkout.mjs` — revised environment destination contract.
- `e2e/fixtures/dashboard.ts`, `e2e/specs/checkin.spec.ts`, `e2e/specs/dashboard.spec.ts` — test-only response/route updates, scoped approval selectors and deterministic elapsed-time timestamps.
- New `e2e/specs/consolidated-dashboard.spec.ts` — counts, filters, contacts, permissions, navigation, export transport.

Backend, relative to `arena-core/`:

- `pom.xml` — `org.apache.poi:poi-ooxml:6.0.0`, compatible with this application's Java 17; see [Apache POI release information](https://poi.apache.org/download.html).
- `src/main/java/com/arena/core/sessions/RunningSessionContracts.java` — masked projection field.
- `src/main/java/com/arena/core/sessions/RunningSessionService.java` — history-only financial restrictions and export paging/transaction.
- `src/main/java/com/arena/core/sessions/RunningSessionController.java` — export endpoint.
- New `src/main/java/com/arena/core/sessions/SessionHistoryWorkbook.java` — typed, role-specific XLSX generation.
- `src/main/java/com/arena/core/publicclub/ClubCustomer{Service,VisitRepository}.java` — academy/session-scoped masked lookup.
- `src/main/java/com/arena/core/publicclub/PublicClubService.java` — shared QR application origin validation.
- `src/main/resources/application.yaml` — origin environment mapping.
- `src/test/java/com/arena/core/sessions/{SessionCheckoutIntegrationTest,PublicClubsIntegrationTest}.java` — workbook/security/environment/contact verification.

Also: `deploy/public-clubs/runtime.env.example` (configuration comments), generated `docs/api/` snapshots/source-review manifests/coverage, and this verification directory. Existing browser suites also regenerate their QA screenshots in their established output locations. No Login application code changed.

## Verification results

See `results.json` for final executed commands/counts. Browser tests use isolated route fixtures; backend tests use isolated H2/JPA transactions and mocked authenticated actors. They do not claim live PostgreSQL/Keycloak/device integration coverage.

- Production Angular build and Playwright TypeScript check passed. Core and Login executable Spring Boot packages also built successfully (`mvn -DskipTests package`, following the executed test suites).
- Frontend unit suites passed: 207 tests, zero failures.
- Core targeted suites passed: 121 tests covering existing billing, stops/pauses, rates, payments, authorization, public QR identity/approval/concurrency/revocation and anti-spam.
- BFF public check-in proxy tests passed: 14 tests, including anonymous handling and host signing/isolation.
- API documentation/security tests passed: 3 Core + 3 Login. OpenAPI schema/references/security/source-review verification passed, Core 137 operations and Login 21.
- New backend workbook tests reopen the actual output with XSSFWorkbook and validate ZIP signature, columns by role, numeric date/duration/currency cells, 105 matching rows across pages, empty searches and cross-tenant empty exports. Contact tests verify masking and pause/resume/refresh, plus manual-start persistence.
- Full affected Playwright suites passed: **112/112**, zero failures, across desktop-light and tablet-dark; responsive checks include 390px mobile. A further **4/4** smoke checks passed against the final rebuilt bundle (facility rendering and Customers navigation/refresh). Screenshots: `floor-desktop-light.png`, `floor-tablet-dark.png` (30 facilities).
- `git diff --check` passed.

Initial checks correctly failed assertions for superseded tenant-host rewriting and hidden Original Bill. Those assertions were updated to the approved requirements. Full browser checks also exposed an ambiguous page-wide Start selector during tab transitions and a real-time assumption in an elapsed-time test. The tests now scope approval to its panel and explicitly fix Date timestamps while leaving Angular bootstrap timers running. A first attempt to pause the clock before Angular bootstrap was discarded because it prevented startup; the final fixed-Date test and complete regression suite pass. No application timer, billing or session regression was identified. A pre-existing Springdoc warning about the error schema's `fieldErrors.additionalProperties` remains; both documentation tests and structural validation pass.

## Limitations and owner checks

- No live PostgreSQL, Keycloak, deployment, physical phone/QR scanner or desktop print-spooler test was performed. The existing simulated-browser QR decoder/PNG download/iframe print tests and backend transactions were executed instead. Validate on an isolated development deployment before release.
- No migration/backfill: old sessions without linked customer visits have no recoverable phone; display name or Walk-in customer. Existing customer retention/deletion continues removing contact data.
- XLSX export materializes the selected matching rows/workbook in memory; unusually large date ranges require appropriate application memory. It is not an asynchronous bulk-export job.
- Existing Reports summary XML export is separate and unchanged; the new genuine XLSX export is explicitly available in Session History.
- Set Core's LAN origin, open Show QR, check its link is `http://192.168.0.219:4200/check-in#…`, then scan from the same Wi-Fi. Confirm name/mobile → Customer Check-ins → staff Start Session → masked contact on Active Floor.
- Confirm Owner and Full Operations Manager see detailed history; Standard Manager/Staff see Original Bill and Bill Paid only, including their own sessions. Export with a player filter and verify all matching rows, including beyond page 1.
- Verify Customers active navigation and direct refresh, floor search/type filters and preserved unfiltered counts. Exercise Pause/Resume/Stop and manual reservations on an isolated club.
