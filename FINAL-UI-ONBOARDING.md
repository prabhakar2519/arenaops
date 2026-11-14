# Final UI, navigation and onboarding refinements

## Changes in this task

Badminton keeps the compact table. Its heading contains saved total/active/inactive counts and a single Add Courts action. The creation form starts hidden, including for an empty club; opening it never generates records. The same action reopens it, and Close court creation hides it. Bulk Edit Rates reveals the draft-only hourly-rate controls. The badminton footer omits duplicated totals and appears for unsaved changes. Snooker retains its prior creation, bulk pricing, table and footer presentation.

Business Profile GET now requires the same Owner/Full Operations Manager authorization as public-field editing. Branding remains separately available to authenticated, active members of the current academy, without granting profile access. Existing personnel restrictions, facility ownership checks, billing restrictions and membership reconciliation remain in force.

| Role | Navigation |
|---|---|
| Owner | Dashboard; Business Profile, Sports, Staff, Facilities, implemented Training Batches; Billing |
| Full Operations Manager | Dashboard; same setup items; no Billing; ordinary personnel only |
| Standard Manager | Dashboard; Sports, staff viewing/assignments, Facilities, applicable Training Batches; no Business Profile or Billing; no personnel administration |
| Staff/Receptionist | Existing permitted Dashboard/home; no setup or Billing links |
| Coach/Instructor | Existing permitted Dashboard/home; no setup or Billing links |

Training Batches retains its existing academy-eligibility condition. No operational placeholder links or new roles were added. The header continues to load tenant branding from the existing protected branding/logo endpoints for all roles.

## Keycloak root cause and fix

The built-in information page resolves action labels with `requiredAction.<id>`. The existing provider's display name is not this translation. The login theme had no `requiredAction.ARENA_STAFF_SETUP` entry, so the internal key was rendered.

Added the missing login message bundle and an information-page override using the existing branded layout. Staff setup now explains verification/password creation and offers Continue account setup. Continuation still uses Keycloak's server-generated action URI, page redirect URI and skip-link rules; there is no script redirect or automatic action execution. Expired/stale/invalid-link, already-verified-email, disabled-account and password-policy messages use understandable wording. Inherited detailed password-policy validation remains available. Errors do not claim to distinguish replay from expiry when Keycloak does not distinguish them.

New setup emails use the supported admin-client overload specifying `arena-ui`, the configured `app.email.frontend-url` plus `/login`, the existing lifespan and all three existing required actions. The custom provider, one-time behavior, email/password checks and application activation reconciliation are unchanged. Account data and token-exchange responses are no longer logged by the Angular callback/auth success paths.

## Files changed in this task

ArenaOps:
- `arena-ui/src/app/facilities/facility-resources.component.ts`, `.html`, `.scss`
- `arena-ui/src/app/sidebar/sidebar.component.ts`
- `arena-ui/src/app/services/workspace-guards.ts` (new)
- `arena-ui/src/app/app.routes.ts`
- `arena-ui/src/app/auth-callback/auth-callback.component.ts`
- `arena-ui/src/app/services/auth.ts`
- `arena-ui/scripts/test-facilities.mjs`, `test-training.mjs`
- `arena-core/src/main/java/com/arena/core/profile/BusinessProfileService.java`
- `arena-core/src/main/java/com/arena/core/service/KeycloakService.java`
- `arena-core/src/test/java/com/arena/core/profile/BusinessProfileTest.java`
- `arena-core/src/test/java/com/arena/core/training/ManagerAuthorizationTest.java`
- `arena-core/src/test/java/com/arena/core/training/AcademyKeycloakIntegrationTest.java` (fixture redirect configuration only; not executed)
- `arena-core/src/test/java/com/arena/core/service/AcademyEmailDiagnosticsTest.java`

Infrastructure:
- `keycloak/themes/arena-login/login/info.ftl` (new)
- `keycloak/themes/arena-login/login/messages/messages_en.properties` (new)
- `keycloak/themes/arena-login/login/resources/css/arena-login-v3.css`
- `keycloak/theme-assets.txt`
- `keycloak/realm/arena-realm.dev.template.json` (explicit local login redirect URIs)
- `tests/test_onboarding_theme.py` (new, static checks only)

Existing unrelated changes in both workspaces were preserved. No provider source, SIT/production realm settings, running services or database records were modified.

## Validation

- Angular production compilation passed.
- Full frontend suite passed (113 tests before new regressions); affected suites rerun with new regressions: Training/navigation 31, Facilities 21. Total current distinct frontend coverage: 116 tests.
- Focused Core suite: 62 tests passed; affected authorization/profile suites rerun with two added regressions: 25 passed. Total current distinct focused Core coverage: 64 tests.
- Three static onboarding theme/manifest/dev-redirect tests passed.
- Both workspace whitespace checks passed.
- No browser screenshots, FreeMarker runtime rendering or live Keycloak activation tests executed. Successful email verification/password setup, expired/replayed links, interrupted activation recovery and post-activation login must be verified against your isolated Keycloak environment after rollout. PostgreSQL transaction tests were not run; existing persistence logic was unchanged.

## Manual rollout/testing steps (not executed)

1. Deploy the Core/UI changes using your usual process; restart Arena Core and refresh/rebuild the frontend as appropriate.
2. Confirm `app.email.frontend-url` points to the browser-accessible UI origin (LAN address when needed). Do not use a server-internal URL.
3. In the existing test/dev realm, confirm Login theme is `arena-login`; keep the existing email theme and custom required action enabled. Deploy the updated theme files. Refresh theme caches or restart Keycloak yourself if cached. No provider JAR rebuild is required.
4. In `arena-ui` Valid Redirect URIs, add the **exact** configured frontend URL plus `/login`, retaining existing callback URIs. The dev template contains localhost/127.0.0.1 entries; changes to a template do not update existing realms. LAN origins need their own exact entry. Do not reset/recreate the realm or widen redirects with wildcards.
5. Send a fresh setup email through the existing authorized resend workflow. Previously sent links retain their original client/redirect information. Test completion, expiry, replay, password validation and disabled accounts; check Staff/Coach landing pages and direct administrative URL/API denial.

No services were started/stopped/restarted, no containers run, no deployment executed, and no commits or pushes made.
