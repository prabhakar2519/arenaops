# ArenaOps Keycloak Setup and Production Operations

> Runtime-secret instructions below describe the previous VPS-file setup. For the updated application workflow and the pending separate infrastructure change, follow [GitHub Environment secrets and VPS deployment](github-environment-secrets.md). Do not create permanent application secret files.

## 1. Purpose

This runbook explains how ArenaOps authentication was designed, how to create the Keycloak environment from an empty server, how the application uses it in production, and how to operate and troubleshoot it safely.

The infrastructure repository is the source of truth for Keycloak runtime configuration, realm templates, the custom login theme, and bootstrap automation. Production users and live credentials are state and are not stored in Git.

## 2. Authentication architecture

```text
Browser
  |  HTTPS /auth/*
  v
Caddy ---------------------------> Keycloak
  |                                  |
  | HTTPS application traffic       | authorization code / tokens
  v                                  v
Arena UI ----------------------> arena-login (BFF)
                                      |
                                      | bearer access token
                                      v
                                  arena-core
                                      |
                                      | client credentials for user administration
                                      v
                                  Keycloak Admin API
```

Component responsibilities:

| Component | Responsibility |
|---|---|
| Caddy | Terminates TLS and routes `/auth/*` to Keycloak and all other traffic to the UI |
| `arena-ui` | Starts login, handles the callback route, and uses the login gateway session |
| `arena-login` | OAuth/OIDC client, authorization-code exchange, server-side session owner, API gateway |
| `arena-core` | Validates JWTs and applies application authorization rules |
| Keycloak | Login UI, identities, passwords, roles, authorization codes, access and refresh tokens |
| PostgreSQL | Persists both ArenaOps data and Keycloak state |

Tokens are kept in the login gateway session. The browser receives a secure session cookie in production.

## 3. Repository layout and ownership

The `arenaops-infrastructure` repository owns:

```text
docker/docker-compose.infra.yaml     PostgreSQL, Keycloak and Caddy
docker/Caddyfile                     TLS entry point and reverse proxy rules
keycloak/realm/*.template.json       Sanitized realm definitions
keycloak/themes/arena-login/         Custom Keycloak login page
scripts/prepare-vps.sh               Directories and Docker network
scripts/bootstrap-keycloak.sh        Initial realm creation and role assignment
scripts/apply-infrastructure.sh      Controlled infrastructure application
.github/workflows/infrastructure.yaml Manual validation/apply workflow
```

The application repository owns the OAuth client configuration used by `arena-login` and JWT validation used by `arena-core`. It does not provision Keycloak.

## 4. Realm design

| Item | Value |
|---|---|
| Realm | `arena` |
| Realm roles | `ADMIN`, `OWNER`, `STAFF` |
| Browser client | `arena-ui` |
| Browser client type | Public client; authorization-code flow |
| Service client | `arena-bff` |
| Service client type | Confidential client with service account |
| Production callback | `https://arenaops.in/login/callback` |
| Production post-logout URI | `https://arenaops.in/` |
| Theme | `arena-login` |

The `arena-bff` service account receives these `realm-management` client roles:

- `manage-users`
- `view-users`
- `view-realm`

These roles allow ArenaOps registration and administration code to create and inspect users. They do not make normal ArenaOps users realm administrators.

## 5. Secret model

Never commit any real secret or a live realm export. The realm template contains `**********` as the `arena-bff` secret. Bootstrap substitutes the live value into a temporary file, imports it, and deletes the temporary file.

| Value | Storage | Consumers |
|---|---|---|
| Database password | `/opt/arenaops/config/infra.env` and `app.env` | PostgreSQL, Keycloak, core |
| Keycloak bootstrap admin password | `/opt/arenaops/config/infra.env` | Initial bootstrap and administration |
| `KEYCLOAK_BFF_SECRET` | Both protected VPS environment files | Keycloak realm, login, core |
| SSH private key | GitHub Environment secret | Deployment workflow only |
| Brevo SMTP credentials | `/opt/arenaops/config/app.env` | Core email service |

Set both VPS files to mode `0600`. Do not print their contents in build or deployment logs.

## 6. Prerequisites from scratch

1. Provision a Linux VPS with ports 22, 80, and 443 allowed.
2. Create a dedicated deployment user with SSH key authentication.
3. Install Docker Engine and the Docker Compose plugin from Docker's official repository.
4. Add the deployment user to the Docker group.
5. Give that user narrowly scoped passwordless sudo access for the reviewed `prepare-vps.sh` operation.
6. Create DNS `A` and, if applicable, `AAAA` records for `arenaops.in` pointing to the VPS.
7. Wait until public DNS resolves to the VPS. Caddy needs this to obtain the TLS certificate.
8. Clone or deploy the infrastructure repository through its workflow.

Check the server before continuing:

```bash
docker version
docker compose version
getent hosts arenaops.in
```

## 7. Create the VPS configuration

Create `/opt/arenaops/config/infra.env` on the VPS:

```dotenv
ARENA_DB_NAME=arena
ARENA_DB_USERNAME=arena
ARENA_DB_PASSWORD=<strong-unique-password>
KEYCLOAK_ADMIN_USER=<bootstrap-admin-name>
KEYCLOAK_ADMIN_PASSWORD=<strong-unique-password>
KEYCLOAK_BFF_SECRET=<long-random-client-secret>
ARENAOPS_DOMAIN=arenaops.in
KEYCLOAK_SSL_REQUIRED=external
```

Generate passwords with an approved password manager or cryptographically secure generator. Do not reuse the database, Keycloak administrator, or client credentials.

Then protect the file:

```bash
chmod 600 /opt/arenaops/config/infra.env
```

Create `/opt/arenaops/config/app.env` with matching database and Keycloak client values:

```dotenv
ARENA_DB_HOST=postgres
ARENA_DB_NAME=arena
ARENA_DB_USERNAME=arena
ARENA_DB_PASSWORD=<same-database-password>
ARENA_DB_SCHEMA=arena

KEYCLOAK_SERVER_URL=http://keycloak:8080/auth
KC_AUTH_URL=https://arenaops.in/auth
KC_JWK_SET_URI=http://keycloak:8080/auth/realms/arena/protocol/openid-connect/certs
KEYCLOAK_BFF_SECRET=<same-arena-bff-secret>

APP_BASE_URL=https://arenaops.in
APP_CORS_ALLOWED_ORIGINS=https://arenaops.in
APP_ADMIN_USERNAMES=<comma-separated-admin-usernames>
SESSION_COOKIE_SECURE=true

ARENAOPS_MAIL_ENABLED=true
BREVO_SMTP_HOST=smtp-relay.brevo.com
BREVO_SMTP_PORT=587
BREVO_SMTP_USERNAME=<brevo-smtp-login>
BREVO_SMTP_PASSWORD=<brevo-smtp-key>
ARENAOPS_MAIL_FROM=<verified-sender-address>
ARENAOPS_MAIL_FROM_NAME=ArenaOps
ARENAOPS_MAIL_REPLY_TO=<reply-address>
ARENAOPS_FRONTEND_URL=https://arenaops.in
```

Protect it with `chmod 600 /opt/arenaops/config/app.env`.

Internal Keycloak URLs use Docker DNS and do not leave the private network. Browser authorization URLs use the public HTTPS address.

## 8. Configure GitHub protection

In `arenaops-infrastructure`, create a GitHub Environment named `production-infrastructure`:

1. Add required reviewers.
2. Restrict deployment to the intended infrastructure branch.
3. Add `VPS_HOST`, `VPS_USERNAME`, and `VPS_SSH_KEY` as Environment secrets.
4. Keep the private key limited to the deployment account.

The workflow cannot apply infrastructure from a normal push. It requires a manual `workflow_dispatch`, operation `apply`, exact confirmation `APPLY`, and Environment approval.

## 9. First production deployment

1. Open **Actions → ArenaOps Infrastructure → Run workflow**.
2. Run `validate` first.
3. Review the workflow result and proposed repository change.
4. Run again with operation `apply` and confirmation `APPLY`.
5. Approve the `production-infrastructure` Environment deployment.

The workflow then:

1. Copies `docker`, `keycloak`, and `scripts` to a temporary VPS directory.
2. Runs `prepare-vps.sh` with sudo.
3. Creates `/opt/arenaops/{infra,app,config,state}` with controlled ownership.
4. Creates the external Docker network named `arenaops` if missing.
5. Synchronizes reviewed infrastructure files to `/opt/arenaops/infra`.
6. Validates Compose using `infra.env`.
7. Starts PostgreSQL, Keycloak, and Caddy.
8. Waits for Keycloak readiness.
9. Creates the `arena` realm when it does not exist.
10. Assigns required service-account roles.
11. Writes `/opt/arenaops/state/infrastructure-applied`.

If the realm already exists, bootstrap deliberately does not overwrite it. Later realm changes must be explicit, reviewed Admin API operations or carefully documented manual changes.

## 10. Initial administrator

After the first realm bootstrap, create the first ArenaOps administrative user through the Keycloak Admin Console or a reviewed Admin API command:

1. Open `https://arenaops.in/auth/admin/`.
2. Sign in with the bootstrap administrator.
3. Select the `arena` realm.
4. Create the user with a verified email and a non-temporary password.
5. Assign the `ADMIN` realm role.
6. Ensure the username is also present in `APP_ADMIN_USERNAMES` if the application currently requires its username allow-list.
7. Restart only the affected application containers after changing `app.env`.

Create a separate named administrator for routine work and retain the bootstrap credential only as a protected recovery credential.

## 11. How login works in production

1. The user clicks **Login** in ArenaOps.
2. `arena-login` starts an authorization-code request for public client `arena-ui`.
3. The browser reaches `https://arenaops.in/auth/...`; Caddy proxies that path to Keycloak.
4. Keycloak renders the `arena-login` theme over the ArenaOps visual background.
5. Keycloak validates the credentials and redirects to `https://arenaops.in/login/callback` with a short-lived code.
6. `arena-login` exchanges the code for tokens and stores them in the server-side session.
7. The browser receives a secure session cookie.
8. Requests under `/api` pass through the login gateway with the access token.
9. `arena-core` validates the JWT using the realm JWK endpoint and applies role/customer access checks.
10. Logout clears the application session and uses Keycloak's logout endpoint, returning the browser to the approved home URI.

## 12. Registration and invitations

1. An administrator creates a customer invitation in ArenaOps.
2. `arena-core` sends the invitation through Brevo when `ARENAOPS_MAIL_ENABLED=true`.
3. The recipient opens the HTTPS registration link.
4. ArenaOps validates the invitation and uses the `arena-bff` service account to create the Keycloak identity.
5. The application stores its own user/customer mapping and assigns the required application role.
6. The user signs in through the normal Keycloak flow.

A `401` during `grantToken` normally means `KEYCLOAK_BFF_SECRET` differs between Keycloak and the application. A `403` during user creation normally means the service account is missing realm-management roles.

## 13. Local development

1. Clone both repositories beside each other.
2. Copy the infrastructure `.env.example` to an ignored `.env` and provide local credentials.
3. Use `arena-realm.local.template.json` for localhost redirect URIs.
4. Start PostgreSQL, Keycloak, and the theme with the infrastructure Compose file.
5. Use the same `KEYCLOAK_BFF_SECRET` in the realm bootstrap and application environment.
6. Copy the application `.env.example` to an ignored `.env`.
7. Start core with the `dev` profile, then login and UI.

The local profile is `dev`; there is no `de` profile. Production always uses `prod`.

## 14. Verification checklist

```bash
curl -fsS https://arenaops.in/auth/realms/arena/.well-known/openid-configuration
curl -I https://arenaops.in/
```

Also verify:

- The certificate is valid and HTTP redirects to HTTPS.
- The login page uses the custom ArenaOps theme.
- Login returns to `/login/callback` and then the home page.
- The header displays the expected parlour and username.
- Protected API calls succeed and unauthenticated calls are rejected.
- Logout invalidates the session and returns home.
- Invitation registration creates a Keycloak user and application mapping.
- A non-admin user cannot access administration functions.

## 15. Routine operations

View status and logs from `/opt/arenaops/infra`:

```bash
docker compose --env-file /opt/arenaops/config/infra.env -f docker/docker-compose.infra.yaml ps
docker compose --env-file /opt/arenaops/config/infra.env -f docker/docker-compose.infra.yaml logs --tail=200 keycloak
```

Back up PostgreSQL on a tested schedule. The realm template recreates configuration only; it does not contain production users, credentials, sessions, or audit history.

## 16. Client-secret rotation

Plan a maintenance window because three places must agree:

1. Generate a new client secret.
2. Update the live `arena-bff` client in Keycloak.
3. Update `KEYCLOAK_BFF_SECRET` in `infra.env` and `app.env`.
4. Recreate `arena-login` and `arena-core` only.
5. Test invitation/user creation and login.
6. Remove the old secret from the password manager after verification.

Never put the secret into the realm template or a GitHub Actions log.

## 17. Troubleshooting

| Symptom | Likely cause | Check or correction |
|---|---|---|
| Login redirect rejected | Callback mismatch | Check exact scheme, host, path, and trailing slash in `arena-ui` |
| Logout does not complete | Post-logout URI mismatch or stale session | Check valid post-logout URIs and client ID |
| `401 Unauthorized` from `grantToken` | Confidential-client secret mismatch | Compare protected values and the live client without logging them |
| User creation returns `403` | Missing service-account roles | Re-run reviewed role assignment for `arena-bff` service account |
| Theme does not appear | Theme mount/realm selection/cache | Check container mount, realm theme setting, and browser cache |
| Core rejects all JWTs | Wrong JWK endpoint or network | Check `KC_JWK_SET_URI` from inside `arena-core` |
| Issuer mismatch | Public and internal URL settings disagree | Check Keycloak hostname and token issuer |
| Caddy cannot obtain TLS | DNS or firewall | Verify DNS, ports 80/443, and Caddy logs |
| Realm bootstrap says it exists | Expected idempotent protection | Apply a reviewed targeted change; do not re-import over production |

## 18. Recovery

For a clean environment, create empty storage, apply infrastructure, create the initial administrator, deploy an application release, and execute the verification checklist.

For production recovery, restore the tested PostgreSQL backup before starting Keycloak. Realm JSON alone cannot restore users or credentials. Treat database restoration and Keycloak version changes as controlled infrastructure operations with backups and a rollback plan.
