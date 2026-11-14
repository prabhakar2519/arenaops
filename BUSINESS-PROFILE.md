# Business Profile — MVP implementation report

Public business details are separate from Customer ownership, legal identity, licensing, billing and trusted notification contacts. The display name falls back to the existing organization/customer name until explicitly saved. Descriptive business type does not change `organizationType`, sports, eligibility, routing or setup calculations. Public contact email is unverified and is not used by identity or notification services.

## Authorization foundation

`AcademyAuthorization` reads current active membership and manager access from the database on every request, under the existing tenant Customer lock. It validates tenant association, Keycloak subject, login state, employment activity and access cutoff. Standard Managers retain existing sports/facilities/batch/coach-assignment access and can read Business Profile. Full Operations Managers can edit public profile/logo and administer ordinary staff login/personnel records. Targets classified as Managers by designation or application roles, and linked Owner accounts, are protected. Role assignment and manager delegation remain Owner-only. No Keycloak roles, providers, realm settings or activation actions were changed.

Manager access is `STANDARD` by default, including existing records after migration. Owners can select `FULL_OPERATIONS` from an existing Manager's application-role drawer. A downgrade applies to the next backend request with the same JWT. The shell refreshes branding/permissions on navigation and window focus; forbidden saves refresh permissions. Backend authorization remains authoritative for already-open forms.

## APIs and routes

- `GET /api/business-profile`: explicit public profile DTO, Customer version and current permissions; Owner/Manager only.
- `PUT /api/business-profile`: one multipart operation containing JSON `profile` and optional `logo`; Owner/Full Operations only. JSON has an explicit allowlist and rejects unknown fields, including tenant/owner/billing fields. `logoAction` is `KEEP`, `REPLACE` or `REMOVE`.
- `GET /api/business-profile/branding`: current tenant display name/logo and the current user's permission flags; active ordinary staff can access this minimal branding DTO. Existing legacy club staff can retrieve their own tenant's branding without gaining administration privileges.
- `GET /api/business-profile/logo`: authenticated current-tenant PNG retrieval; no caller-controlled tenant or file path. Private/no-store cache and nosniff headers.
- `PUT /api/academy/staff/{id}/login/manager-access`: Owner only, tenant scoped, versioned `{version, accessLevel}`. Target must have the Manager application role. Changes are audited.
- UI `/business-profile` is the real Business Profile destination, including clubs/gyms. `/academy/setup` continues to serve the original setup checklist/review; Dashboard setup links remain unchanged.

View mode is default. Edit supports validation, upload preview, replacement/removal, one Save Changes action, Cancel, failed-save draft preservation, stale-update reload, route guards and browser reload protection. The header updates directly from a successful saved response. Existing shell typography, green accent, responsive layout, theme toggle and user menu are reused.

## Storage and consistency

Logo pixels are stored privately in PostgreSQL `business_logo.image` (`bytea`), keyed by the existing Customer ID. The application generates an opaque revision UUID for the retrieval URL. PNG/JPEG/WebP decoding checks actual content, not supplied MIME or filenames. Accepted pixels are re-encoded to PNG without uploaded metadata; input and normalized output must each be at most 2 MiB, each dimension at most 4096, and total pixels at most 4,194,304. TwelveMonkeys ImageIO WebP 3.13.0 provides WebP decoding; H2 is test-only.

Fields, logo replacement/removal and audit records share the existing database transaction. Failed persistence or audit rolls everything back, retaining the old logo. There are no independently staged durable files, old-file deletion races, or filesystem orphans requiring retry cleanup. Multipart staging is temporary only; committed logos never depend on container `/tmp`.

Core multipart limits are 2 MB/file and 3 MB/request. Login forwards raw multipart bytes instead of consuming them in its servlet resolver and bounds profile uploads to 3 MiB even without Content-Length. Its existing session/token authentication, explicit CORS origins and SameSite=Lax cookies are reused. The existing BFF has Spring CSRF protection disabled; this feature uses PUT (not cross-site HTML form POST) and does not alter global authentication/security configuration. Keep credentialed CORS restricted to trusted application origins.

The infrastructure PostgreSQL service mounts `postgres_data:/var/lib/postgresql/data`; committed images therefore use the existing persistent volume. No infrastructure configuration was changed. Repository instructions require environment-specific PostgreSQL backups, but no scheduled backup/restore implementation was found. Operators must include the Customer and business_logo tables in database backups, retain off-host copies, protect database credentials/backups and verify restoration. A Docker volume alone is not a backup.

## Additive Liquibase migration

`R1_10_academy_training/04-business-profile.xml`, included by the master changelog:

1. Adds `academy_staff.manager_access`, NOT NULL with `STANDARD` default and an allowed-value check.
2. Adds nullable public fields to Customer: business_display_name, business_type, public_contact_email, public_phone, address_line, city, region, postal_code, country and logo_revision.
3. Adds `business_logo` with Customer foreign key, one logo per tenant, non-null bytes and a 2 MiB database constraint.

Existing identity/contact/type fields and setup business logic are retained. Optional fields begin empty; trusted contact emails are deliberately not copied to public fields. The existing Customer version is reused, so unrelated Customer writes may also cause a legitimate optimistic conflict. No data reset or destructive migration was performed.

## Files in this task

Created:

- `arena-core/src/main/java/com/arena/core/profile/{BusinessProfileController,BusinessProfileService,ProfileUpdate,BusinessLogo,BusinessLogoRepository,LogoImages}.java`
- `arena-core/src/main/resources/db/changelog/features/R1_10_academy_training/04-business-profile.xml`
- `arena-core/src/test/java/com/arena/core/profile/{BusinessProfileTest,BusinessProfileTransactionTest}.java`
- `arena-core/src/test/java/com/arena/core/training/ManagerAuthorizationTest.java`
- `arena-core/src/test/resources/profile/valid-logo.webp`
- `arena-login/src/test/java/com/arena/login/controller/BusinessProfileProxyTest.java`
- `arena-ui/src/app/business-profile/business-profile.{component.ts,component.html,component.scss,service.ts}`
- `arena-ui/scripts/test-business-profile.mjs`
- `BUSINESS-PROFILE.md`

Modified:

- Core: `pom.xml`, `CustomerEntity`, `AcademyStaff`, `AcademyAuthorization`, `TrainingService`, `StaffLoginService`, `StaffLoginController`, `ErrorCode`, `GlobalExceptionHandler`, `application.yaml`, master changelog and `StaffLoginServiceTest`.
- Login: `application.yaml`, `TokenController`, `ErrorCode`.
- UI: `package.json`, `api-error.ts`, `services/auth.ts`, header TS/HTML/SCSS, sidebar, routes, training service/component TS/HTML.

No files deleted by this task. Earlier uncommitted work is preserved.

## Validation and deployment

Validation completed: Core 116 passed (146 discovered, 30 external PostgreSQL/Keycloak tests skipped); Login/BFF 18 passed; frontend 65 passed (including 13 new profile checks); Angular production compilation passed. New Liquibase XML and the master changelog parsed successfully; `git diff --check` passed. No live migration or browser end-to-end run was performed. Tests cover authorization, current-JWT downgrade, protected targets and tenant association; DTO allowlist/validation; PNG/JPEG/WebP decoding and bounds; safe tenant logo retrieval; transactional rollback after flush and logo changes; stale and concurrent saves; audit metadata; multipart proxy behavior; view/edit/cancel/navigation/save failures/logo preview/header updates. Transaction tests use isolated H2 in-memory databases and the actual transaction annotation through a Spring proxy; they do not start HTTP services or use deployment databases.

Manual deployment/runtime verification remains the operator's responsibility:

1. Back up each environment's database and review/apply the additive Liquibase migration before serving the new mappings. Verify PostgreSQL-specific migration and constraints in the target environment; tests do not mutate SIT/dev PostgreSQL.
2. Deploy Core, Login and the UI together, including the WebP decoder dependency. Verify multipart forwarding through the deployed proxy (and any external body-size limits).
3. Existing Managers default to Standard. The Owner should explicitly grant Full Operations where appropriate; there is no automatic privilege elevation.
4. Verify active Owner/Standard/Full/Coach/Staff accounts, a downgrade with an open form, cross-tenant attempts, uploads/removal, long names, both themes, mobile layout and setup-review links with real sessions.
5. Confirm durable database backups and a restore test for logo bytes.

Services requiring an operator-managed deployment/restart: Arena Core, Arena Login/BFF and the frontend server/assets. PostgreSQL and Keycloak require no restart for this feature. No services or containers were started, stopped, restarted, recreated or deployed; no commit or push was performed. Browser end-to-end verification and live PostgreSQL/Keycloak checks were intentionally not run.
