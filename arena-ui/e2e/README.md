# Snooker browser QA

This suite changes no application code. Fixture tests run the real built Angular app and intercept **every** API request with isolated synthetic state; they test browser behavior, not backend authorization or billing correctness. Real Core/H2 tests are recorded separately. The approved policy grants aggregate/report access to Owner and Full Operations Manager, and limits Recent Sessions to20 entries after unsettled filtering. Failures are reported normally; none are marked as expected failures.

## Run

From `arena-ui`:

```
npm ci
npx playwright install chromium
npm run build
npm run e2e
npm run e2e:headed
npm run e2e:report
```

Config starts a loopback-only static server on4317. For an already running frontend use `E2E_BASE_URL=http://localhost:4200 npm run e2e`; fixture API requests are still intercepted. Use only DEV/test frontend builds. The default server uses the built `dist/arena-ui/browser` bundle, so rebuild after application changes. Set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` only if using an existing compatible browser installation. Otherwise Playwright uses its managed Chromium.

Projects: desktop1440/light and tablet820/dark-green, headless by default. HTML report: `playwright-report/index.html`. JSON: `test-results/results.json`. Failures retain screenshots/traces and attach synthetic request logs; runtime browser exceptions fail tests. No hardcoded waiting sleeps: assertions retry, API events synchronize, timer/poll tests advance Playwright's controlled clock. Synthetic API model is deliberately scoped and not a replacement implementation of the backend.

## Real API read-only checks

Create dedicated QA accounts manually using supported onboarding; assign actual Owner, Full Operations Manager, Standard Manager and Staff roles. Authenticate each through the normal Keycloak+BFF flow with Playwright codegen and save storage state into ignored `e2e/.auth`. Example:

```
npx playwright codegen --save-storage=e2e/.auth/owner.json http://localhost:4200
E2E_LIVE=1 E2E_LIVE_BASE_URL=http://localhost:4200 E2E_AUTH_OWNER=e2e/.auth/owner.json npm run e2e
```

Use `E2E_AUTH_FULL_MANAGER`, `E2E_AUTH_MANAGER`, `E2E_AUTH_STAFF` for the other accounts. No passwords/tokens are committed, no localStorage profile impersonation is used for live tests, no cookie state is borrowed from a user's browser. Storage state captures the BFF cookie; treat it as a credential. Missing states are reported as skipped/blocked, never passes. A live Full Manager test checks the current operations capability before asserting the approved financial policy.

Live suite performs only dashboard/authority/collection reads and anonymous access checks. Note the existing dashboard GET expires past reservations as normal application behavior. No live sessions/payments/customers are created or deleted. The three live mutation journeys are explicitly blocked until a supported dedicated tenant seed/reset mechanism is supplied. Never point mutation automation or cleanup at shared SIT/PROD. No destructive cleanup implementation is included.

Generated traces/storage states can contain private data in live mode; keep them ignored and access-controlled. Only synthetic screenshots/aggregate reports should be shared in git. `docs/qa/snooker-dashboard` contains the audit, matrix and execution report.
