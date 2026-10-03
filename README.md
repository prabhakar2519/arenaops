# ArenaOps

ArenaOps consists of an Angular UI, a Spring Boot login gateway, and a Spring Boot core API. PostgreSQL, Keycloak, TLS termination, and one-time VPS setup are maintained in the separate `arenaops-infrastructure` repository.

## Local development

Provision local PostgreSQL and Keycloak from the infrastructure repository. Copy `.env.example` to the repository-root `.env`, fill in your local credentials and run `chmod 600 .env`. Both services load this ignored file automatically when the `dev` profile is active; exported environment variables take precedence. Use Java properties syntax (no `export` and no quotes around values), and escape literal backslashes as `\\`. Then start the services in separate terminals:

```bash
cd arena-core && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd arena-login && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd arena-ui && npm ci && npm run start:dev
```

The `dev` profile means local developer overrides. There is no `de` profile. Production does not import the local `.env`; it receives runtime configuration through the GitHub Environment deployment workflow. Restart a service after editing `.env`.

## Delivery

Pull requests into `main` run tests and builds. Pushes to `main`, `feature/*`, and `bugfix/*` never deploy production. A push to a valid `release/vX.Y.Z` branch publishes immutable container images and deploys them through the protected `production` GitHub Environment.

See [Keycloak setup](docs/arenaops-keycloak-setup.md) and [CI/CD and infrastructure](docs/arenaops-cicd-infrastructure.md).
