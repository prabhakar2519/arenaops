# ArenaOps CI/CD and Production Pipeline

## 1. Purpose

This runbook describes the ArenaOps delivery pipeline from developer change through production deployment. It separates application releases from long-lived infrastructure so a routine release cannot restart or recreate PostgreSQL, Keycloak, Caddy, or VPS configuration.

## 2. Repository model

| Repository | Owns | Production action |
|---|---|---|
| `arenaops` | Angular UI, login gateway, core API, tests, Dockerfiles, application Compose, release workflow | Builds immutable images and replaces application containers |
| `arenaops-infrastructure` | VPS preparation, Docker network, PostgreSQL, Keycloak, Caddy, realm templates and theme | Manually applies reviewed infrastructure changes |

Application and infrastructure containers join the external Docker network `arenaops`. This lets the application reach `postgres` and `keycloak` through Docker DNS without exposing those services publicly.

## 3. Production topology

```text
Internet
   |
   | 80/443
   v
Caddy
   +-- /auth/* ----------> Keycloak:8080
   +-- all other paths --> arena-ui:80
                              |
                              +--> arena-login:7700
                                      |
                                      +--> arena-core:7701
                                              |
                                              +--> PostgreSQL:5432

All containers: external Docker network "arenaops"
Only Caddy: public 80/443
UI health port: 127.0.0.1:8081
Keycloak diagnostic port: 127.0.0.1:9091
```

## 4. Branch strategy

```text
feature/* or bugfix/*
       |
       | push / pull request: build and test only
       v
      main
       |
       | create reviewed version branch
       v
release/vX.Y.Z
       |
       | build, publish, approval, deploy
       v
   production
```

| Trigger | Validate | Publish images | Deploy production |
|---|---:|---:|---:|
| Push `feature/**` | Yes | No | No |
| Push `bugfix/**` | Yes | No | No |
| Pull request to `main` | Yes | No | No |
| Push `main` | Yes | No | No |
| Manual application workflow | Yes | No | No |
| Push `release/v*` | Yes | Yes | Yes, after Environment approval |

The publish and deploy jobs both check the full Git ref starts with `refs/heads/release/v`. A manual workflow run cannot deploy.

## 5. What was implemented

### Application repository

- Maven verification for `arena-core` and `arena-login`.
- Reproducible Angular installation with `npm ci` and production build.
- Dockerfiles and `.dockerignore` files for all three services.
- Production Compose validation.
- GHCR image publishing with an immutable version tag derived from the release branch.
- Protected production deployment through SSH.
- A deployment script that records current and previous releases.
- A UI health check after container replacement.
- Production Spring profiles with required environment-based secrets.
- Secret-safe `.env.example` and Git exclusions.

### Infrastructure repository

- PostgreSQL 16, Keycloak 26.5.3, and Caddy 2.8 Compose services.
- Persistent Docker volumes.
- Private shared network named `arenaops`.
- Sanitized production and local realm templates.
- Custom Keycloak theme.
- Idempotent realm bootstrap and service-account role assignment.
- Manual infrastructure workflow with typed confirmation and protected approval.

## 6. One-time GitHub setup

### Application repository Environment

Create an Environment named `production`:

1. Add required reviewers.
2. Add deployment branch rule `release/v*`.
3. Add Environment secrets:
   - `VPS_HOST`
   - `VPS_USERNAME`
   - `VPS_SSH_KEY`
   - `GHCR_USERNAME`
   - `GHCR_TOKEN`
4. Give `GHCR_TOKEN` only the package-read access required by the VPS.

The workflow-provided `GITHUB_TOKEN` has package-write permission only in the image publishing job.

### Infrastructure repository Environment

Create `production-infrastructure`:

1. Add required reviewers.
2. Restrict it to the intended infrastructure branch.
3. Add `VPS_HOST`, `VPS_USERNAME`, and `VPS_SSH_KEY`.

## 7. One-time VPS setup

1. Provision the VPS and create a dedicated deployment user.
2. Configure SSH key authentication and disable unsafe access according to the server baseline.
3. Install Docker Engine and Compose.
4. Add the deployment user to the Docker group.
5. Point production DNS at the server.
6. Create `/opt/arenaops/config/infra.env` and `app.env`.
7. Set both configuration files to `0600`.
8. Run the infrastructure workflow validation and approved apply operation.
9. Confirm PostgreSQL, Keycloak, and Caddy are healthy.
10. Complete the Keycloak initial-administrator checklist.

The infrastructure preparation script creates:

```text
/opt/arenaops/infra    Reviewed infrastructure files
/opt/arenaops/app      Application deployment files
/opt/arenaops/config   Protected environment files
/opt/arenaops/state    Deployment state and release history
```

## 8. Configuration ownership

`/opt/arenaops/config/infra.env` contains infrastructure-only runtime values:

- Database name, user, and password
- Keycloak bootstrap administrator
- `KEYCLOAK_BFF_SECRET`
- Production domain

`/opt/arenaops/config/app.env` contains application runtime values:

- Database connection and schema
- Keycloak public/internal URLs and shared client secret
- Application URL, CORS origins, and admin usernames
- Secure session-cookie setting
- Brevo SMTP connection and sender configuration

GitHub stores deployment credentials, not application runtime secrets. Git contains placeholders only.

## 9. Normal development flow

1. Create `feature/<description>` or `bugfix/<description>` from current `main`.
2. Make the change locally.
3. Run relevant Maven tests and the UI build.
4. Push the branch. CI runs validation only.
5. Open a pull request into `main`.
6. Review code, workflow changes, database migrations, and secret exposure risk.
7. Merge only after required checks pass.

Workflow or deployment changes deserve the same review as application code because they can alter production behavior once included in a release branch.

## 10. CI validation flow

The application `validate` job executes in this order:

1. Check out the exact commit.
2. Install Temurin Java 21 with Maven caching.
3. Run `mvn clean verify` for core.
4. Run `mvn clean verify` for login.
5. Install Node 20 with npm caching.
6. Run `npm ci` and `npm run build` for the UI.
7. Provide non-secret validation placeholders.
8. Run `docker compose ... config --quiet` against production application Compose.

No image is published if validation fails.

The infrastructure validation job checks shell syntax, parses all realm JSON templates, and renders its Compose configuration with non-secret placeholder values.

## 11. Create a production release

After `main` contains the approved change:

```bash
git switch main
git pull --ff-only
git switch -c release/v1.0.0
git push -u origin release/v1.0.0
```

Use a new semantic version for each release. Do not move or reuse a release branch after it has deployed; create the next version instead.

The workflow derives:

```text
IMAGE_REPOSITORY=ghcr.io/<github-owner>/<repository>
IMAGE_TAG=v1.0.0
```

It rejects branch-derived tags that do not match the semantic-version pattern.

## 12. Image build and publication

After validation, the publish job:

1. Logs in to GHCR with the workflow `GITHUB_TOKEN`.
2. Builds each service from its own directory.
3. Pushes:

```text
ghcr.io/<owner>/<repo>/arena-core:v1.0.0
ghcr.io/<owner>/<repo>/arena-login:v1.0.0
ghcr.io/<owner>/<repo>/arena-ui:v1.0.0
```

Production Compose references the exact repository and version. It does not deploy `latest`.

## 13. Production deployment flow

The deployment waits at the protected `production` Environment. After reviewer approval it:

1. Copies only `docker-compose.prod.yaml` and `deploy-app.sh` to `/opt/arenaops/app`.
2. Connects to the VPS through the dedicated SSH account.
3. Verifies the release coordinates and protected `app.env` exist.
4. Logs the VPS Docker client into GHCR using the package-read credential.
5. Runs the deployment script.
6. Records the prior image coordinates as `previous-app-release`.
7. Records the requested coordinates as `current-app-release`.
8. Pulls the three immutable images.
9. Recreates only `arena-core`, `arena-login`, and `arena-ui`.
10. Polls `http://127.0.0.1:8081/` for up to 90 seconds.
11. Fails the job and prints Compose status if the UI never becomes healthy.

PostgreSQL, Keycloak, and Caddy are not part of application Compose and are not restarted by a release.

## 14. Runtime behavior after deployment

1. Caddy accepts HTTPS traffic.
2. `/auth/*` reaches Keycloak.
3. Other browser requests reach the Angular UI.
4. UI API/authentication requests are proxied to `arena-login`.
5. Login holds the user session and sends access tokens to core.
6. Core validates JWTs, talks to PostgreSQL, performs migrations, and sends invitation email through Brevo.

All application containers use the `prod` Spring profile. Missing required production values cause startup failure instead of falling back to committed credentials.

## 15. Infrastructure-change flow

Infrastructure changes never ride with an application release:

1. Create a pull request in `arenaops-infrastructure`.
2. Let validation check scripts, JSON, and Compose.
3. Review operational impact and backup requirements.
4. Merge through the repository's normal review process.
5. Run the workflow manually with operation `validate`.
6. For an approved change, run operation `apply`, enter `APPLY`, and approve the Environment gate.

The apply workflow synchronizes infrastructure files and starts the declared services. Realm bootstrap creates a missing realm and ensures service-account roles, but does not overwrite an existing realm.

## 16. Database migrations

Liquibase migrations run when `arena-core` starts. Before a release containing schema changes:

1. Review forward and backward compatibility.
2. Take and verify a PostgreSQL backup.
3. Prefer additive changes that allow the previous application version to run temporarily.
4. Document any migration that prevents application rollback.
5. Observe core startup logs before declaring the release complete.

## 17. Post-deployment verification

Verify each release:

- GitHub deployment job is green.
- `docker compose ps` shows all three application containers running.
- `https://arenaops.in/` loads with a valid certificate.
- Login and callback complete.
- Authenticated home page displays the parlour and username correctly.
- A protected API request succeeds.
- Logout clears the session.
- Invitation email and registration work when changed by the release.
- Keycloak and PostgreSQL containers did not restart unexpectedly.

## 18. Application rollback

The deployment script writes:

```text
/opt/arenaops/state/current-app-release
/opt/arenaops/state/previous-app-release
```

Read the previous file, export its repository and tag, and run the same deployment script:

```bash
cat /opt/arenaops/state/previous-app-release
export IMAGE_REPOSITORY=<previous-repository>
export IMAGE_TAG=<previous-tag>
/opt/arenaops/app/deploy/deploy-app.sh
```

Then repeat the post-deployment checklist. If the failed release applied a non-backward-compatible database migration, restore or remediate the database using the migration-specific plan rather than blindly starting an older image.

## 19. Infrastructure rollback and recovery

Infrastructure rollback is operation-specific. Do not attempt to restore PostgreSQL or Keycloak state by replaying an older Compose file over live volumes.

- For configuration errors, apply a reviewed corrective commit.
- For corrupted state, restore a tested PostgreSQL backup.
- For certificate issues, correct DNS/firewall/Caddy configuration while preserving its data volume.
- For Keycloak changes, use explicit Admin API remediation and confirm user access afterward.

## 20. Troubleshooting

| Failure | Check |
|---|---|
| Maven or UI CI failure | Run the exact workflow commands locally with Java 21 and Node 20 |
| Production jobs skipped | Confirm the event is a push and ref is `release/v...` |
| Environment approval missing | Check required reviewers and deployment-branch rules |
| Image push denied | Check workflow package permissions and repository package policy |
| VPS image pull denied | Check `GHCR_USERNAME`, token read permission, and package visibility |
| SSH step fails | Check host, deployment user, key, firewall, and server authorization |
| `app.env` missing | Create the protected file; do not weaken the workflow check |
| Compose reports missing network | Apply infrastructure or create the reviewed `arenaops` network |
| UI health check fails | Inspect application Compose status and logs for all three services |
| Core fails on startup | Check DB connectivity, Liquibase, required environment variables, and JWK URL |
| Login fails | Check Keycloak discovery, redirect URI, cookie security, and client secret |
| Email fails | Check mail enable flag, Brevo SMTP credentials, verified sender, and core logs |

## 21. Production readiness checklist

- [ ] DNS points to the VPS.
- [ ] Firewall exposes only required ports.
- [ ] Docker and Compose are installed and maintained.
- [ ] Dedicated deployment account and SSH key are configured.
- [ ] GitHub Environments require reviewers.
- [ ] Application release branches are restricted to `release/v*`.
- [ ] VPS environment files exist with mode `0600`.
- [ ] No real secret exists in Git history or workflow logs.
- [ ] PostgreSQL backups are automated and restore-tested.
- [ ] Keycloak administrator and service-client secrets are protected.
- [ ] GHCR read credential has minimal scope.
- [ ] First infrastructure apply and Keycloak bootstrap are complete.
- [ ] Initial ArenaOps administrator is verified.
- [ ] Login, logout, API authorization, invitations, and registration are tested.
- [ ] Rollback commands and previous release coordinates are available.

## 22. Confluence publishing notes

Create two Confluence pages under the ArenaOps operations space:

1. **ArenaOps Keycloak Setup and Production Operations**
2. **ArenaOps CI/CD and Production Pipeline**

Paste each Markdown document using Confluence's Markdown import or convert the tables and fenced blocks with the editor. Restrict the page containing operational paths and secret names to the engineering/operations group. Keep values as placeholders; passwords, keys, tokens, and live environment-file contents belong only in the approved secret store.
