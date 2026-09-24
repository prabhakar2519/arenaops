# ArenaOps – CI/CD & Infrastructure Pipeline

## Repositories

| Repository | Responsibility |
|---|---|
| `arenaops` | UI, login, core, tests, images, release deployment |
| `arenaops-infrastructure` | VPS/network, PostgreSQL, Keycloak, Caddy, realm bootstrap |

Application releases never provision infrastructure.

## Branch and release flow

```text
Developer
   |
feature/* or bugfix/*
   | Pull Request + CI
   v
main
   | create reviewed release branch
   v
release/v1.0.0
   |
GitHub Actions + production approval
   |
   v
Hetzner VPS
```

CI runs on feature/bugfix pushes, `main`, release pushes, and pull requests into `main`. Publishing and deployment require a push whose full ref starts with `refs/heads/release/v`. Feature branches, bugfix branches, pull requests, `main`, and manual runs cannot enter production jobs.

The requested release-branch mechanism is implemented. Signed tags or published GitHub Releases are harder to move accidentally and provide stronger immutability; they are the recommended later improvement, but adopting them now would change the requested branch strategy.

## Application workflow

Validation runs Maven `clean verify` for core/login, `npm ci` plus UI build, and production Compose validation. A valid `release/vX.Y.Z` push then publishes three immutable GHCR images tagged `vX.Y.Z`, waits for the protected `production` environment, copies only app deployment files, verifies the VPS `.env`, recreates only app containers, and checks the UI. PostgreSQL and Keycloak are not restarted.

## Infrastructure workflow

```text
Infrastructure Repository
        |
        | PR: validation only
        | manual apply + typed APPLY + approval
        v
Infrastructure Pipeline
        +--> VPS directories/network
        +--> PostgreSQL
        +--> Keycloak
        +--> Caddy/TLS
```

Normal pushes do not apply infrastructure. Validation checks shell syntax, realm JSON, and Compose.

## GitHub configuration

Create environments:

- `production`: required reviewers; deployment branch rule `release/v*`.
- `production-infrastructure`: required reviewers; restrict to the infrastructure default branch.

Application repository secret names:

- `VPS_HOST`, `VPS_USERNAME`, `VPS_SSH_KEY`
- `GHCR_USERNAME`, `GHCR_TOKEN`

Infrastructure repository secret names:

- `VPS_HOST`, `VPS_USERNAME`, `VPS_SSH_KEY`

The workflow `GITHUB_TOKEN` publishes packages. `GHCR_TOKEN` should be a narrowly scoped read-packages credential used by the VPS when packages are private.

## VPS configuration

`/opt/arenaops/config/infra.env` contains database credentials, Keycloak bootstrap credentials, `KEYCLOAK_BFF_SECRET`, and `ARENAOPS_DOMAIN`.

`/opt/arenaops/config/app.env` contains database connection values, Keycloak URLs/secret, application URL/origins/admin names, secure-session settings, and Brevo SMTP credentials/sender.

Both files must be owned by the deployment user and mode `0600`. Use a dedicated SSH deployment user with key authentication and only required Docker/path permissions. Store its private key in GitHub Environment secrets.

## Triggers

Normal CI: push a feature branch or open/update a PR into `main`.

Production:

```bash
git switch main
git pull --ff-only
git switch -c release/v1.0.0
git push -u origin release/v1.0.0
```

Infrastructure: run `validate`, then after review run `apply`, type `APPLY`, and approve the protected environment. This is never part of normal application deployment.

## Rollback

The application deploy script records current and previous image coordinates under `/opt/arenaops/state`. Export the previous repository/tag and rerun `/opt/arenaops/app/deploy/deploy-app.sh`. Verify login, UI, and a read-only API request. Review Liquibase compatibility and take a database backup before migrations; an old application may not run against a newer schema.

Infrastructure rollback is operation-specific. Do not revert PostgreSQL volumes or Keycloak state by replaying old Compose files; restore tested backups or apply an explicit reviewed remediation.

## Troubleshooting

| Failure | Action |
|---|---|
| Maven/UI CI failure | Run the same commands locally with Java 21/Node 20 |
| Image pull denied | Check GHCR visibility and token read permission |
| Deployment blocked | Check branch name and environment approval |
| Missing environment file | Create the protected VPS file; do not weaken checks |
| UI health failure | Inspect Compose status and application logs |
| Keycloak unavailable | Operate it from infrastructure repository only |
