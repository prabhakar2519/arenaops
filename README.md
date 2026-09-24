# ArenaOps

Fresh ArenaOps skeleton repository.

## Modules

- `arena-login`: authentication gateway shell
- `arena-core`: core API shell
- `arena-ui`: minimal Angular home page
- `ref-data`: seed/reference data placeholder

## Local Infra

```bash
docker compose --profile infra up -d
```

Keycloak runs at `http://localhost:9091/auth` and Postgres runs on `127.0.0.1:5432`.
