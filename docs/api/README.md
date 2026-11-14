# ArenaOps API reference

The code-first specifications are [Core](arenaops-core-openapi.yaml) and [Login/BFF](arenaops-login-openapi.yaml). Read [coverage and audit limits](COVERAGE.md) before assuming runtime exception-path coverage. Metadata version 1.0.0 describes the MVP reference; only facilities currently use a `/api/v1` URI prefix. Existing unversioned routes are not renamed.

Spring Boot 3.4.2 uses springdoc 2.8.13, within the [supported 2.7–2.8 compatibility range](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot). OpenAPI 3.1 is selected explicitly. DTO and Bean Validation inference supplies request/response types; controller annotations supply scope and service rules. Documentation-only records describe raw facility JSON and Map responses without altering request parsing or application DTOs.

## Local Swagger access (manual)

Documentation is **disabled by default**. It is denied at the security chain even if the Login BFF catch-all could otherwise forward it. When you next start your own local services, add `api-docs` to your normal local profiles, e.g. `SPRING_PROFILES_ACTIVE=dev,api-docs`; use your existing local environment/configuration. No services were started or restarted to generate these files.

- Core: `http://localhost:7701/swagger-ui/index.html`, `/v3/api-docs`, `/v3/api-docs.yaml`.
- Login: `http://localhost:7700/swagger-ui/index.html`, `/v3/api-docs`, `/v3/api-docs.yaml`.
- `servers: /` means the origin serving the document. For an imported specification, set your chosen service origin explicitly in the API client; relative URLs do not imply a shared server.
- `api-docs` enables anonymous documentation access **only**. Do not activate it on publicly reachable, SIT or production deployments. No SIT/production profile enables it. Use an authenticated ingress/private documentation export if documentation is needed in those environments; no such infrastructure change is made here.
- The dedicated documentation security chain is separate from API execution authorization. Disabled documentation requests use Spring Security denial rather than the business error envelope.

## Authentication and safe interactive use

Core APIs use a valid Keycloak Bearer JWT. Swagger Authorize accepts an existing authorized test token; it does not request a password grant or include credentials. Never commit tokens, passwords, invitation codes, SMTP credentials or session cookies in documentation or API-client collections. Authorization persistence is disabled.

Login uses `JSESSIONID`, not a browser-supplied Bearer token. Sign in using the existing ArenaOps UI PKCE flow against the same BFF origin, then use Swagger with that session. Swagger sends existing credentials; browsers prevent JavaScript from manufacturing HttpOnly cookies. POST `/api/token` exchanges a fresh browser authorization code and PKCE verifier and returns UserInfo only, keeping access/refresh tokens server-side. Synthetic examples are not usable credentials. No new Keycloak client, OAuth2 password flow or privileged documentation user is introduced.

Swagger Try it out executes real mutations with the caller's permissions. Use isolated test fixtures. Keycloak's verify-email/password/action-token pages are native Keycloak behavior, not imaginary ArenaOps API endpoints. Existing `ARENA_STAFF_SETUP` and invitation security are unchanged.

## Core versus BFF and aliases

Use Core's specification for resource APIs. Through Login, the BFF delegates the same `/api/...` paths/verbs/bodies using its session token, enforces session checks and sanitizes error responses. Its catch-all is intentionally hidden as an individual OpenAPI operation. Public forwarding exceptions are registration POSTs and `/api/public/**`; the latter is a routing allowance, **not evidence that any public resource endpoints exist in Core**. Core has no implemented `/api/presence/*` handlers even though BFF presence touches are best-effort; none are invented in the specification.

Login documents explicit root and `/api` aliases and supported trailing slashes. `/api/me` is a separate legacy Spring OAuth2-principal inspection route and does not validate a BFF session; use `/api/user` for that purpose. `/register` returns 302 with a Location pointing to the configured frontend. GET `/api/health` is plain text `OK` in Login but JSON `{status: UP}` in Core.

## Regeneration and drift checks (no running server)

Prerequisites: JDK 17+, Maven, Python 3, and the pinned Python packages:

```sh
python3 -m pip install -r docs/api/requirements.txt
scripts/export-openapi.sh --check
```

The export tests build **mock MVC documentation-only contexts** with business services/RestTemplate mocked. They have no HTTP listener, database, SMTP, Keycloak or payment connections. The approved YAML snapshots, source-review fingerprints and coverage report are version-controlled. Normal checks do not overwrite them.

After editing controllers, DTOs, validation, service rules, security or error handling, review the documentation implications and explicitly regenerate:

```sh
scripts/export-openapi.sh --update
# Inspect the generated YAML, source-review manifest and coverage diff.
git diff -- docs/api
```

The optional arguments after the mode are passed to Maven. In a restricted workspace with a pre-populated writable Maven cache:

```sh
scripts/export-openapi.sh --check -o -Dmaven.repo.local=/tmp/arena-openapi-m2
```

Maven `test`/CI already enforce byte-for-byte Springdoc snapshot parity and reviewed source hashes. The Python check validates against the vendored [official OpenAPI 3.1 schema](https://spec.openapis.org/oas/3.1/schema/2022-10-07), validates component JSON Schemas and local references, rejects duplicate YAML keys/operation IDs, checks path parameters/security/error examples and controller coverage. The GitHub validation job runs it after the normal Core/Login builds. `--update` is never invoked by CI.

The reference schema is the official OpenAPI Initiative artifact, not a homegrown replacement. No network access is needed for the structural validation itself. Ignored local development/mail configuration files are excluded from source-review fingerprints; their values must never enter the reference. Source-review manifests deliberately require review when service/security logic changes even if a Java DTO does not change. Regenerating a hash is not a substitute for that review.

For an optional manual live comparison after you have started local services yourself:

```sh
curl --fail http://localhost:7701/v3/api-docs.yaml -o /tmp/arenaops-core-live.yaml
curl --fail http://localhost:7700/v3/api-docs.yaml -o /tmp/arenaops-login-live.yaml
```

Do not overwrite approved files directly with a live export; use the mock export so server origins and operation ordering remain reproducible.

## Requests, errors and examples

Inspect the operation description first: it includes role/tenant constraints and conditional business validation. Path/query/header defaults and validation come from Spring parameters and Bean Validation. Examples are synthetic structural examples; substitute existing tenant-scoped IDs and valid business fixtures. Protected fields and supplied amounts/tenant identifiers must not be inferred from generic object samples.

- Facility generation: required UUID `Idempotency-Key`; counts/types are scoped to the registered sport. Limits come from context (defaults 50 per type and 200 combined). Validated same-key/same-payload replay returns the original 201 response; a different payload conflicts. Retain the original key during uncertain retries.
- Facility PATCH: IDs/versions plus changed fields; omitted fields remain unchanged; null clears name/hourly/frame pricing, but never active status. Numeric rates are positive with at most two decimals; courts have no non-null frame pricing. Save is atomic. A 409 sample lists conflicting codes and versions. Preserve drafts and fetch latest state before an explicit retry.
- Profile updates: multipart `profile` JSON part plus optional logo. Use the JSON part content type `application/json`. KEEP/REPLACE/REMOVE behavior and image limits are described by the operation. Whole-request and normalized-image limits differ between BFF and Core.
- Pagination: zero-based `page`, `size` 1..100, optional filters. Page envelopes contain content, totalElements, totalPages, number, size. Their content DTOs are documented per module.
- Error dropdowns have named examples generated from the actual service ErrorCode enum, with exact HTTP status and description. `x-error-catalog` lists the declared taxonomy; it is not a claim that each code is reachable from every operation. The handler-specific error annotation determines the advertised business cases.
- Ordinary null error fields are omitted. FacilityFailure adds reasonCode/fieldErrors/conflicts. Login can sanitize unrecognized downstream errors to a generic status-derived code; do not expect every upstream body field to survive.
- Global routing/negotiation outcomes (404/405/406) are described in `x-routing-errors`, not blindly attached to every controller method. 413 is used for logo limits; 422 and 429 are not implemented.

The current springdoc version emits a non-fatal `cloneViaJson`/`additionalProperties` warning on repeated OpenAPI 3.1 generation for map-valued schemas. Mock JSON/YAML endpoints and Swagger assets return 200 and exported artifacts validate, but this generator compatibility warning remains open.

Swagger schemas cannot prove mutable tenant entitlements, concurrency correctness, required-action replay behavior or external dependency outcomes. These remain the responsibility of business/security tests and isolated runtime testing. The coverage report records remaining verification limits rather than claiming 100% runtime coverage.

## API client import and maintenance

Import either YAML into Postman or another OpenAPI 3.1-capable API client. Set Core/Login base origins separately. Use a private environment for temporary tokens/cookies and never export its values to version control.

New handlers must carry Operation metadata and relevant business-error annotations; mock mapping coverage fails when an endpoint lacks documentation. Add a precise response projection when returning an untyped Map or raw JSON. Keep doc-only models synchronized with parser behavior. Document 201 only where controllers really return it (billing order creation currently returns 200). Do not add endpoints for unimplemented features. Review changes to source-review fingerprints and exported YAML alongside the implementation PR.
