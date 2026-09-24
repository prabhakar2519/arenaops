# ArenaOps – Keycloak Setup & Configuration

## Purpose and architecture

This document describes how ArenaOps authenticates users and how production can be recreated without storing credentials in Git.

```text
Browser -- OIDC authorization code --> Keycloak /auth
Browser -- session cookie -----------> arena-login (BFF/session owner)
arena-login -- bearer access token --> arena-core (resource server)
arena-core -- client credentials ----> Keycloak Admin API
```

OAuth tokens remain in the login gateway session. `arena-core` validates Keycloak JWTs and uses the confidential `arena-bff` service account for owner and staff account administration.

## Existing implementation reviewed

The original setup used realm-export JSON, Docker Compose, and `scripts/bootstrap-keycloak-arena.sh`. It imported the realm on first setup and normalized some client settings through Keycloak Admin REST calls. Docker mounted the custom `arena-login` theme. Redirects and service-account roles were partly automated; DNS, TLS, initial administrators, and credentials remained manual.

| Item | Configuration |
|---|---|
| Realm | `arena` |
| Realm roles | `ADMIN`, `OWNER`, `STAFF` |
| Browser client | `arena-ui`, public client, authorization-code flow |
| Backend client | `arena-bff`, confidential client with service account |
| Login gateway | Exchanges codes, stores access/refresh tokens, proxies `/api` |
| Core API | Spring resource server using the realm JWK set |
| Admin integration | `arena-bff`; `manage-users`, `view-users`, `view-realm` |
| Theme | `keycloak/themes/arena-login` in the infrastructure repository |

## Repository ownership

Stored in Git:

- Sanitized realm templates with a masked confidential-client secret.
- Custom login theme and pinned Keycloak image version.
- Idempotent bootstrap scripts.
- Production redirect/origin definitions.

Never stored in Git:

- Keycloak administrator password or `arena-bff` client secret.
- SMTP keys, API keys, user passwords, sessions, or tokens.
- Live realm exports containing credential values.

## Local development

1. Clone `arenaops-infrastructure` beside the application repository.
2. Copy its `.env.example` to `.env` and set local credentials.
3. Use the local realm template and start PostgreSQL/Keycloak with its Compose file.
4. Set the same `KEYCLOAK_BFF_SECRET` for bootstrap, core, and login.
5. Copy the application `.env.example` to `.env` and start each module.

The existing `dev` profile means developer-machine overrides. There is no `de` profile; this work does not introduce or rename one. Common settings stay in `application.yaml`, local behavior in `application-dev.yaml`, and required production values in `application-prod.yaml`.

## Production design

```text
Local development
      |
      v
Sanitized realm template + bootstrap scripts (Git)
      |
      v
Manual infrastructure workflow
      |
      v
Hetzner VPS
  +-- Caddy/TLS
  +-- Keycloak
  +-- PostgreSQL
  +-- ArenaOps application containers
```

Production uses `https://arenaops.in`, callback `https://arenaops.in/login/callback`, and post-logout URI `https://arenaops.in/`. PostgreSQL, Keycloak, Caddy, and application containers share the private Docker network `arenaops`. Caddy sends `/auth/*` to Keycloak and other requests to the UI.

## Provisioning

Infrastructure apply is manual. It requires `workflow_dispatch`, operation `apply`, typed confirmation `APPLY`, and approval on the `production-infrastructure` GitHub Environment.

Bootstrap creates a missing realm but does not overwrite an existing realm. It injects `KEYCLOAK_BFF_SECRET` into a temporary realm file, deletes that file, and applies required service-account roles. Existing-realm changes should use explicit reviewed Admin API operations instead of full realm re-imports.

## Variables and secrets

| Variable | Consumer | Secret |
|---|---|---|
| `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` | Bootstrap | Yes |
| `KEYCLOAK_BFF_SECRET` | Keycloak, core, login | Yes |
| `KEYCLOAK_SERVER_URL`, `KC_AUTH_URL`, `KC_JWK_SET_URI` | Application | No |
| `APP_BASE_URL`, `APP_CORS_ALLOWED_ORIGINS` | Login/UI | No |
| `ARENAOPS_DOMAIN` | Caddy/Keycloak | No |

Runtime secrets live in root-owned VPS files `/opt/arenaops/config/infra.env` and `/opt/arenaops/config/app.env`, mode `0600`. GitHub contains only deployment access secrets. Rotate a client secret by updating Keycloak and both application consumers during one maintenance window.

## One-time production actions

1. Point DNS to the Hetzner VPS.
2. Install Docker Engine and Compose from Docker’s official repository.
3. Create both protected VPS environment files.
4. Configure protected GitHub Environments and reviewers.
5. Run the infrastructure workflow explicitly.
6. Create the initial ArenaOps administrator and assign `ADMIN`.
7. Verify TLS, login, logout, owner registration, and service-account user creation.

## Troubleshooting

| Symptom | Check |
|---|---|
| `401` from `grantToken` | Client secret must match live `arena-bff` |
| User creation `403` | Service-account realm-management roles |
| Invalid redirect URI | Exact scheme, host, port, path, trailing slash |
| Logout error | Post-logout URI and client ID |
| Theme unchanged | Theme mount, realm setting, browser cache, CSS version |
| JWT failure | Issuer/JWK URL from inside the container |

## Recovery and recreation

Restore the PostgreSQL backup when users and realm state must be preserved. For a clean rebuild, start empty PostgreSQL/Keycloak storage, run the manual infrastructure workflow, recreate the initial admin, deploy an application release, and verify discovery, login, API authorization, logout, and invitations. The realm template recreates configuration, not production users; database backups are required for full recovery.
