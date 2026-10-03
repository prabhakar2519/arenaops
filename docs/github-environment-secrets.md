# GitHub Environment secrets and VPS deployment

GitHub Environment secrets are the source of truth. Running Docker containers necessarily receive runtime secrets; these values remain accessible to privileged Docker administrators. The updated application workflow creates a mode-0600 file under VPS `/dev/shm`, starts the containers, and removes the file on exit. It does not require a permanent `/opt/arenaops/config/app.env`. This is not a claim that secrets never reach the VPS.

## Application repository: implemented

Configure the `production` GitHub Environment with required reviewers and release branch rules:

- `VPS_HOST`, `VPS_USERNAME`, `VPS_SSH_KEY`
- `GHCR_USERNAME`, `GHCR_TOKEN` (package-read access)
- `ARENAOPS_APP_ENV`: multiline dotenv content, using actual values in GitHub only. All values below are masked examples.

```dotenv
ARENA_DB_HOST=postgres
ARENA_DB_NAME=arena
ARENA_DB_USERNAME=arena
ARENA_DB_PASSWORD='[MASKED]'
ARENA_DB_SCHEMA=arena
KEYCLOAK_SERVER_URL=http://keycloak:8080/auth
KC_AUTH_URL=https://arenaops.in/auth
KC_JWK_SET_URI=http://keycloak:8080/auth/realms/arena/protocol/openid-connect/certs
KEYCLOAK_BFF_SECRET='[MASKED]'
APP_BASE_URL=https://arenaops.in
APP_CORS_ALLOWED_ORIGINS=https://arenaops.in
APP_ADMIN_USERNAMES=arena_admin
SESSION_COOKIE_SECURE=true
ARENAOPS_MAIL_ENABLED=true
BREVO_SMTP_HOST=smtp-relay.brevo.com
BREVO_SMTP_PORT=587
BREVO_SMTP_USERNAME='[MASKED]'
BREVO_SMTP_PASSWORD='[MASKED]'
ARENAOPS_MAIL_FROM=noreply@arenaops.in
ARENAOPS_MAIL_FROM_NAME=ArenaOps
ARENAOPS_FRONTEND_URL=https://arenaops.in
ARENAOPS_BILLING_MONTHLY=1000
ARENAOPS_BILLING_ANNUAL=10000
ARENAOPS_BILLING_TAX_RATE=0.18
```

Use single quotes for literal secrets containing dollar signs/hashes. Do not paste real credentials into repository files, logs or chat. Ensure the DB password and BFF client secret match the live infrastructure. Sample billing prices still require business review.

Application workflow behavior:

| Trigger | Effect |
|---|---|
| Push feature/bugfix/main | Tests/builds only |
| Pull request to main | Tests/builds only |
| Manual application workflow | Validation only |
| Push release/vX.Y.Z | Validate, publish three application images, production Environment approval, application deployment |

Application deployment does not recreate PostgreSQL/Keycloak/Caddy. GitHub masks the encoded runtime bundle; SSH transfers it to a fresh memory-backed directory. Cleanup runs on script exit, and the GHCR login is removed. A host/process crash can leave a tmpfs file until cleanup/reboot; operators may remove abandoned `/dev/shm/arenaops-app.*` directories. Do not remove a directory while a deployment is running. Existing old secret files are not automatically deleted.

## Separate infrastructure repository: inspected, change pending approval

Repository: `prabhakar2519/arenaops-infrastructure`.
Workflow: **ArenaOps Infrastructure**, `.github/workflows/infrastructure.yaml`.
Current workflow expects `/opt/arenaops/config/infra.env`; it does not yet read `ARENAOPS_INFRA_ENV` from GitHub. A prepared patch changes it to temporary `/dev/shm` configuration and safe dotenv parsing. Applying it requires approval for the separate repository.

After that patch is applied, configure the `production-infrastructure` Environment with reviewers, allowed branch, `VPS_HOST`, `VPS_USERNAME`, `VPS_SSH_KEY`, and `ARENAOPS_INFRA_ENV` containing:

```dotenv
ARENA_DB_NAME=arena
ARENA_DB_USERNAME=arena
ARENA_DB_PASSWORD='[MASKED]'
KEYCLOAK_ADMIN_USER=bootstrap-admin
KEYCLOAK_ADMIN_PASSWORD='[MASKED]'
KEYCLOAK_BFF_SECRET='[MASKED]'
ARENAOPS_DOMAIN=arenaops.in
```

Use unquoted or single-quoted one-line values; the proposed bootstrap parser rejects duplicate keys and double-quoted values and never evaluates dotenv as executable shell. The proposed workflow requires Docker/Compose, Python 3, jq, curl and rsync on the VPS.

## When to trigger infrastructure

Run **ArenaOps Infrastructure → Run workflow → operation=validate** first, then **operation=apply, confirmation=APPLY**, with Environment approval:

1. First VPS/environment provisioning, before the first application release.
2. A reviewed Keycloak/PostgreSQL/Caddy image, network, mount, TLS/proxy or host configuration change.
3. Recovery or migration to a new VPS, following a verified database backup/restore plan.

Do not trigger it for routine application releases, customer invitations/users, trials, subscription payments, or normal VPS restarts. Containers restart automatically with persistent volumes. Applying infrastructure is repeatable but may recreate services when configuration changes; it is not a destructive database reset. Never delete volumes to re-run bootstrap.

Realm templates create a missing realm, skip replacement of an existing realm, and ensure required service-account permissions. Editing the JSON alone does not update a live realm. Client/role/redirect/secret changes in an existing realm require explicit reviewed Keycloak Admin API/console operations. Changing GitHub database/client secrets alone does not rotate existing DB or Keycloak credentials.

## Realm configuration

- `keycloak/realm/arena-realm.template.json`: production `arena` realm, HTTPS callbacks.
- `keycloak/realm/arena-realm.local.template.json`: local callbacks.
- Clients: public `arena-ui` (authorization code), confidential `arena-bff` (service-account user management).
- Realm roles: `OWNER`, `STAFF`, `ADMIN`.
- Both have `users: []`; no customer users or user passwords are imported. Keycloak creates the BFF service-account identity automatically.
- `arena-bff.secret` is `**********` in Git; bootstrap injects the GitHub-provided value.
- Bootstrap grants `manage-users`, `view-users`, `view-realm` to the BFF service account.

## New VPS sequence

1. Provision Linux and a deployment user; install Docker/Compose, Python 3, jq, curl and rsync. Configure SSH, Docker access and reviewed sudo access for `prepare-vps.sh`.
2. Point production DNS to the VPS; allow SSH and HTTP/HTTPS. Keep PostgreSQL private and Keycloak's diagnostic port bound to localhost.
3. Configure both GitHub Environments and matching runtime secrets. Apply the separate infrastructure patch before relying on GitHub-only infrastructure configuration.
4. Run infrastructure validate, then apply/APPLY and approve its Environment gate.
5. Verify Keycloak discovery at `https://arenaops.in/auth/realms/arena/.well-known/openid-configuration`.
6. Create the first named ArenaOps administrator in Keycloak, assign ADMIN, and match `APP_ADMIN_USERNAMES` if using the application's allowlist. Realm JSON intentionally does not create this user.
7. Push an approved application `release/vX.Y.Z` branch, approve production deployment, then verify UI/login/invitation flow.
8. Schedule and verify PostgreSQL backups. Realm JSON cannot recover production users or passwords; those reside in the database.

No production deployment has been triggered by these local changes. Pushing the current feature branch runs application validation only.
