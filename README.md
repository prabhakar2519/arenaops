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

Existing local `.env` files must set `ARENA_DB_NAME=arena_dev` and `ARENA_DB_SCHEMA=arena_dev` and match the DEV infrastructure database. This configuration change does not move any existing data.

Start each service in its own terminal:

```bash
cd arena-core && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd arena-login && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd arena-ui && npm ci && npm run start:dev
```

Restart a backend after changing `.env`. DEV uses Keycloak at `http://localhost:9091/auth`. The UI's checked-in public runtime file contains local-only defaults. Authentication uses PKCE S256 and validates callback state.

## Delivery

Feature/bugfix pushes and pull requests into main run CI only. A push to `main` (including a merged PR) runs CI, publishes commit-SHA images and automatically deploys SIT using the `sit` GitHub Environment. A reviewed `release/vX.Y.Z` branch push runs CI, publishes version/SHA images and deploys PROD through the protected `production` Environment. Configure required reviewers and allow only `release/v*` branches on that Environment; keep SIT without approval gates for automatic deployment. Release runs fail closed if the production reviewer policy is missing or cannot be verified.

Manual workflow runs validate only. GitHub Release creation and tag pushes are not deployment triggers; this repository uses release **branch pushes**. Infrastructure must already be healthy in each target environment before application deployment.

The UI image is identical across SIT and PROD. Its Nginx entrypoint writes only public environment/realm configuration to `arena-config.js`, served with `Cache-Control: no-store`. No backend/database/mail/registry secret is sent to the UI.

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

The application workflow supplies `arena_sit` for main/SIT and `arena` for release/PROD. `deploy/write-runtime-env.py` includes the required property, and `deploy/config.py` rejects missing, blank or incorrect environment schema values before SSH/deployment. Core receives it through its private runtime env file; BFF/UI do not receive database configuration. Set the GitHub database-name secret in both application and infrastructure environments to the convention above, with matching credentials. No PROD database was changed or deployed.

Run `./deploy/validate-db-schema.sh` for real PostgreSQL verification: it creates disposable local databases with no application schemas, runs all core tests including fresh/repeated startup and Hibernate schema validation, checks compatibility with an existing `arena` changelog/data fixture, and removes its container. Use `./deploy/validate-db-schema.sh -Dtest=DatabaseSchemaStartupTest,BillingPersistenceTest` for focused CI database checks. Only a random loopback port is published; test credentials are disposable. `./deploy/validate.sh` also checks changelog references and deployment schema injection.

Changing a schema setting on an existing installation does not migrate tables or Liquibase history. DEV/SIT databases containing data under the old `arena` schema require a separate reviewed migration; this change targets clean bootstrap. Keep PROD configured as `arena` and review its actual changelog version/checksums and privileges before any future release. No checksum reset, data move or destructive migration is included.
