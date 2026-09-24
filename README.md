# ArenaOps

ArenaOps consists of an Angular UI, a Spring Boot login gateway, and a Spring Boot core API. PostgreSQL, Keycloak, TLS termination, and one-time VPS setup are maintained in the separate `arenaops-infrastructure` repository.

## Local development

Provision local PostgreSQL and Keycloak from the infrastructure repository, copy `.env.example` to `.env`, then start the services:

```bash
cd arena-core && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd arena-login && mvn spring-boot:run
cd arena-ui && npm ci && npm run start:dev
```

The `dev` profile means local developer overrides. There is no `de` profile.

## Delivery

Pull requests into `main` run tests and builds. Pushes to `main`, `feature/*`, and `bugfix/*` never deploy production. A push to a valid `release/vX.Y.Z` branch publishes immutable container images and deploys them through the protected `production` GitHub Environment.

See [Keycloak setup](docs/arenaops-keycloak-setup.md) and [CI/CD and infrastructure](docs/arenaops-cicd-infrastructure.md).
