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

Before the first core startup, create the application `arena` schema in its database using your approved DB administration process. Existing Liquibase migrations and tracking remain in that schema; Keycloak uses its own public schema. This requirement applies to every environment, including a fresh DEV database.

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
