# ArenaOps

ArenaOps consists of an Angular UI, a Spring Boot login gateway and a Spring Boot core API. The separate `arenaops-infrastructure` repository manages PostgreSQL, Keycloak, Caddy/TLS and VPS preparation. Application deployment never applies infrastructure.

| Environment | Public URL | Keycloak realm | Spring profile | Application project | External network |
| --- | --- | --- | --- | --- | --- |
| DEV | `http://localhost:4200` | `arena-dev` | `dev` | Local processes | Local infrastructure uses `arenaops-dev` |
| SIT | `https://sit.arenaops.in` | `arena-sit` | `sit` | `arenaops-sit-app` | `arenaops-sit` |
| PROD | `https://arenaops.in` | `arena` | `prod` | `arenaops-prod-app` | `arenaops-prod` |

## Local development

Start DEV infrastructure in its repository using `./scripts/dev.sh start`. Copy this repository's `.env.example` to `.env`, fill the blank credentials and run `chmod 600 .env`. Both services import that ignored file only with the `dev` profile; exported environment variables take precedence. Use Java properties syntax without `export` or value quotes; escape literal backslashes as `\\`.

Match the local PostgreSQL credentials and `KC_BFF_CLIENT_SECRET` to DEV infrastructure. Existing `.env` files must rename `KEYCLOAK_BFF_SECRET` to `KC_BFF_CLIENT_SECRET` and `KEYCLOAK_SERVER_URL` to `KC_BASE_URL`, set `KC_REALM=arena-dev`, and remove or update any old `KC_JWK_SET_URI` pointing at `arena`. The credential contents are not migrated automatically.

Set the required `ARENA_DB_SCHEMA` in local configuration; no schema creation command is needed. The core initializes the configured schema before Liquibase creates its tracking tables, then applies the same changelog in every environment. Keycloak continues to use its public schema.

| Environment | `ARENA_DB_NAME` | `ARENA_DB_SCHEMA` |
| --- | --- | --- |
| DEV | `arena_dev` | `arena_dev` |
| SIT | `arena_sit` | `arena_sit` |
| PROD | `arena` | `arena` |

Local application DEV settings live in each service's ignored `src/main/resources/application-dev.yaml`. Copy `application-dev.yaml.example` to that filename on a new checkout and fill its local credentials. Set `ARENA_DB_NAME=arena_dev` and `ARENA_DB_SCHEMA=arena_dev` to match the DEV infrastructure database. Infrastructure continues to use its separate `.env`. Application DEV profiles do not import `.env`.

Start each service in its own terminal:

```bash
cd arena-core && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd arena-login && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd arena-ui && npm ci && npm run start:dev
```

Restart a backend after changing its `application-dev.yaml`. DEV uses Keycloak at `http://localhost:9091/auth`. The UI's checked-in public runtime file contains local-only defaults. Authentication uses PKCE S256 and validates callback state. The local core DEV YAML keeps Liquibase disabled, matching the working local session while pending database changes are resolved.

Open the development UI at `http://localhost:4200` or its LAN address. DEV supports HTTP LAN origins using the same PKCE S256 challenge with secure random values and a JavaScript SHA-256 implementation when SubtleCrypto is unavailable. Allow the exact callback by running `python3 scripts/dev-lan.py 192.168.0.219` in the infrastructure repository. The DEV server binds to all interfaces and proxies `/auth` to Keycloak, keeping browser login, theme assets and form submissions on the UI host. Restart the DEV server after changing proxy configuration. Keycloak's internal canonical issuer stays unchanged for backend token validation. SIT and PROD still require HTTPS.

## Delivery

Feature/bugfix pushes and pull requests into main run CI only. A push to `main` (including a merged PR) runs CI, publishes commit-SHA images and automatically deploys SIT using the `sit` GitHub Environment. A reviewed `release/vX.Y.Z` branch push runs CI, publishes version/SHA images and deploys PROD through the protected `production` Environment. Configure required reviewers and allow only `release/v*` branches on that Environment; keep SIT without approval gates for automatic deployment. Release runs fail closed if the production reviewer policy is missing or cannot be verified.

Manual runs default to validation only. To deploy, select `operation=deploy`, the target environment, and enter confirmation `DEPLOY`. Manual SIT deployments from the selected ref publish `sha-${GITHUB_SHA}` images. Manual PROD deployments require a valid `release/vX.Y.Z` branch and the same protected production approval/reviewer policy as automatic release pushes; main, tags and invalid release branches are rejected before publishing. GitHub Release creation and tag pushes are not deployment triggers; this repository uses release **branch pushes**. Infrastructure must already be healthy in each target environment before application deployment.

The UI image is identical across SIT and PROD. Its Nginx entrypoint writes only public environment/realm configuration to `arena-config.js`, served with `Cache-Control: no-store`. No backend/database/mail/registry secret is sent to the UI.

Public QR check-in requires a cryptographically random `PUBLIC_CLUB_HOST_KEY` of at least 32 characters in each application's GitHub Environment (`sit` / `production`). Core and BFF receive the same environment-specific secret; the browser never receives it. This is required even while Public Club Website and public reservation requests are disabled. Use independent keys for SIT and PROD. Deployment validation now rejects a missing/short key before SSH or service changes. Configuring this key does not rotate facility QR identities or publish club websites.


Detailed deployment and authentication notes are maintained locally in the ignored `docs/` directory.

## Validation

Requires Java 21 for CI parity, Node 20.19+, Python 3 and Docker Compose **2.30+** (raw env-file support and `up --wait`). Local Maven compilation targets Java 17; production Docker/CI use Java 21.

```bash
mvn --batch-mode -f arena-core/pom.xml clean verify
mvn --batch-mode -f arena-login/pom.xml clean verify
(cd arena-ui && npm ci && npm test && npm run build)
./deploy/validate.sh
./deploy/validate-ui.sh
```

The deployment tests use dummy values and mocked commands; UI container checks publish no host ports and use an isolated network. Database-backed billing tests need their explicit test database variables and otherwise skip. Real VPS deployment and end-to-end Keycloak login remain operator verification steps. The ignored `implementation_plan/implementation_plan.md` records local changes and exact checks.

## Shared VPS ingress

One infrastructure-owned `arenaops-edge-caddy` serves `sit.arenaops.in` and `arenaops.in` on 80/443 and joins `arenaops-sit` / `arenaops-prod`. Each application stack joins only its own network. Containers and canonical aliases are `sit-arena-core`, `sit-arena-login`, `sit-arena-ui` and their `prod-` equivalents. Internal calls use the same environment's `<env>-keycloak` / `<env>-postgres` aliases.

Caddy routes `/api` and `/api/*` to the matching BFF, the selected realm (`arena-sit` or `arena`) and `/auth/resources/*` to matching Keycloak, and remaining paths to matching UI. Other auth realms/admin/management paths are blocked. UI Nginx refuses API/auth paths directly. The shared edge manages TLS for both domains. Deploy the matching infrastructure aliases before or alongside these application changes; see the infrastructure README for legacy proxy retirement and certificate migration. No deployment was performed for this change.

## Database schema initialization

`ARENA_DB_SCHEMA` is the sole schema input. `application.yaml` binds it without a fallback to Liquibase's default schema, changelog parameter `ARENA_DB_SCHEMA`, and Hibernate's `default_schema`. DEV imports the root `.env`; SIT/PROD inherit the same bindings and never load local files. JDBC does not need a separate `currentSchema` setting.

`DatabaseSchemaConfiguration` validates a nonblank lowercase PostgreSQL identifier of at most 63 characters and executes `CREATE SCHEMA IF NOT EXISTS` with a quoted identifier before SpringLiquibase initialization. This ordering allows fresh tracking-table creation in the selected schema. It preserves existing schemas and never renames, moves or drops application tables. The database user needs CREATE privilege on its database and appropriate rights to the configured schema; the database itself is provisioned by infrastructure.

All table, column, index, sequence, foreign-key, precondition, SQL and rollback schema references use `${ARENA_DB_SCHEMA}`. The original changeSet IDs and paths remain intact; the schema-creation changeSet remains idempotent as well. Tracking tables stay in the configured application schema.

The application workflow supplies `arena_sit` for the resolved SIT target and `arena` for the resolved PROD target, including manual deployment. `deploy/write-runtime-env.py` includes the required property, and `deploy/config.py` rejects missing, blank or incorrect environment schema values before SSH/deployment. Core receives it through its private runtime env file; BFF/UI do not receive database configuration. Set the GitHub database-name secret in both application and infrastructure environments to the convention above, with matching credentials. No PROD database was changed or deployed.

Run `./deploy/validate-db-schema.sh` for real PostgreSQL verification: it creates disposable local databases with no application schemas, runs all core tests including fresh/repeated startup and Hibernate schema validation, checks compatibility with an existing `arena` changelog/data fixture, and removes its container. Use `./deploy/validate-db-schema.sh -Dtest=DatabaseSchemaStartupTest,BillingPersistenceTest` for focused CI database checks. Only a random loopback port is published; test credentials are disposable. `./deploy/validate.sh` also checks changelog references and deployment schema injection.

Changing a schema setting on an existing installation does not migrate tables or Liquibase history. DEV/SIT databases containing data under the old `arena` schema require a separate reviewed migration; this change targets clean bootstrap. Keep PROD configured as `arena` and review its actual changelog version/checksums and privileges before any future release. No checksum reset, data move or destructive migration is included.

## Application pipeline event rules

| Event / inputs | Result | Image tag | GitHub Environment |
| --- | --- | --- | --- |
| PR into main | Validate only | None | None |
| Push/merge to main | Validate, publish, deploy | `sha-<commit>` | `sit` |
| Push to valid `release/vX.Y.Z` | Validate/reviewer-policy check, publish, approval, deploy | `vX.Y.Z` plus SHA tag | `production` |
| Manual `operation=validate` | Validate only | None | None |
| Manual SIT `deploy` + `DEPLOY` | Validate, publish selected commit, deploy | `sha-<commit>` | `sit` |
| Manual PROD `deploy` + `DEPLOY` on valid release branch | Validate/release-policy check, publish, approval, deploy | Release version plus SHA tag | `production` |

For a manual deployment, choose the ref under GitHub Actions → Run workflow, select the environment and `deploy`, then type `DEPLOY`. Wrong confirmation fails before build/test jobs perform deployment work. Production permits only the existing release-branch policy; immutable tag deployment is not enabled. Required reviewers and `release/v*` deployment branch restrictions must remain configured on the `production` GitHub Environment. Both production paths check reviewer configuration before publishing and recheck after approval before VPS access.

The validated target is passed to image tagging, GitHub Environment selection, schema/runtime injection and deployment concurrency (`arenaops-sit-application` or `arenaops-production-application`). Secrets still come from the selected Environment and temporary protected runtime files; deployment scripts and pinned SSH host verification are unchanged. No `latest` images are used and no production deployment was performed while implementing these rules.

## Human administrator authorization

Application administration requires the exact Keycloak realm role `ADMIN` in `realm_access.roles`. A matching username alone grants no privileges. The BFF recognizes ADMIN, OWNER, COACH and STAFF, prioritizing ADMIN when multiple roles exist; the UI supports the same role names. Master-realm bootstrap accounts manage Keycloak infrastructure and are not application accounts.

The infrastructure repository bootstraps one initial human admin per environment using Variables `INITIAL_ADMIN_USERNAME` / `INITIAL_ADMIN_EMAIL` and Secret `INITIAL_ADMIN_PASSWORD` on the infrastructure GitHub Environment. New users must change the temporary password at first login; PROD additionally requires TOTP setup. Existing users keep their password and required actions on reapply. No human-admin password enters application runtime configuration or Angular. Configure additional users/admins through normal lifecycle operations. Existing username-only administrators must receive the ADMIN realm role before deploying this application revision.

## Academy training setup

Academy owners can use `/academy/sports`, `/academy/staff` and `/academy/batches`. An `ACADEMY` organization type or customer plan enables the module. First owner login opens incomplete setup unless skipped; Sports and Continue training setup stay available. Desktop uses a sidebar and mobile a collapsible academy menu. Existing club, billing and court/table paths are preserved. This release base contains facility navigation but no implemented court/table screens; capability-specific links reuse the existing Play Areas path.

Sports offers 42 seeded catalogue activities, searchable multiple selection, academy-local Other activities, training/session capability settings and safe deactivation. Badminton supports court sessions; Snooker and Pool/Billiards support table sessions. Sport pages contain Overview, Batches and Coaches tabs. Sports registrations do not replace the customer's subscription plan or legacy sports field.

Staff uses existing APP_USER/Keycloak identities with separate academy memberships, COACH/STAFF roles and sport assignments. An existing exact username/email is reused without changing its password. New staff use Keycloak account-action invitations for password setup and email verification. The membership commits before email delivery; `STAFF_INVITATION_FAILED_SAVED` means it exists and Send invitation retries delivery. Configure Keycloak realm SMTP for real invitation delivery; application customer-onboarding SMTP settings do not configure Keycloak SMTP.

Batches can be drafted without coaches/schedules. Activation requires at least one active academy coach assigned to the sport and a valid weekly schedule. An optional lead must be among assigned coaches. Schedules use the academy's IANA timezone (default Asia/Kolkata), require increasing times and valid effective dates, and reject internal overlaps and assigned-coach conflicts with other active batches in that academy. Historical schedule rows and inactive batches are retained. Facility allocation, student management and attendance are outside this module.

The existing BFF forwards `/api/academy/**` to core. Backend derives academy ownership from the authenticated OWNER's active APP_USER/customer record and checks entitlement. No caller-supplied academy ID grants access. List APIs support pagination and search/status filtering. Updates carry `version`; stale writes return 409. Academy row locks serialize mutations, including simultaneous batch activations. Composite foreign keys prevent cross-academy sport/coach joins. Current management screens/APIs are owner-only; coaches/staff receive membership and identity roles but no setup-management permission.

Migration `db/changelog/features/R1_10_academy_training/01-academy-training.xml` uses `${ARENA_DB_SCHEMA}` throughout. It adds customer timezone/skipped settings plus SPORT_CATALOGUE, ACADEMY_SPORT, ACADEMY_STAFF, ACADEMY_STAFF_SPORT, TRAINING_BATCH, BATCH_COACH and BATCH_SCHEDULE. Existing data is preserved; Liquibase applies and seeds once.

Local checks:

```bash
npm --prefix arena-ui test
npm --prefix arena-ui run build -- --configuration production
bash deploy/validate.sh
bash deploy/validate-db-schema.sh  # disposable local PostgreSQL; full backend/migration suite
bash deploy/validate-ui.sh         # local isolated Nginx containers; academy deep routes
mvn --batch-mode -f arena-login/pom.xml verify
```

The Confluence-ready design is maintained locally in `docs/academy-training-setup.md`, excluded from Git with other documentation folders. Changes stay local for review; no infrastructure deployment is required to review them.

## Academy Setup MVP v1.0

See [implementation, authorization, migrations and local installation](ACADEMY-MVP.md) and [gap analysis](MVP-PLAN.md). The feature adds the dashboard checklist, per-sport operations, optional staff identities, multi-role access and tenant play areas. Install the Keycloak provider/email theme and apply Liquibase before running the new workflow.
