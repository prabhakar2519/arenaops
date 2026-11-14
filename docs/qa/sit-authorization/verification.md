# SIT Owner dashboard authorization repair

## Diagnosis

The application deployment completed successfully, but signed-in Owner requests to academy dashboard, branding and check-in APIs returned `403 ACCESS_DENIED`.

Read-only diagnostics of SIT showed:

- Core, BFF, UI, Keycloak and PostgreSQL containers healthy.
- Core logs recorded the denied request references.
- Both `arena-ui` and `arena-bff` lacked the built-in `basic` default client scope.
- The realm had the built-in `basic` scope and its `oidc-sub-mapper`.
- Owner accounts were active, linked to an academy, had valid matching identity links where present, and had the OWNER realm role. No account/contact details or credentials were output.

Keycloak 26 supplies the access-token subject (`sub`) through `basic`. The academy authorization and branding services require a stable subject, while older account-access checks can accept a preferred username. This explained successful login with rejected academy operations. The previous application health checks did not test an authenticated Owner journey. Infrastructure templates/bootstrap already contain the correct scope configuration, but the existing running SIT realm predated it; application deployment does not update that realm.

## Minimal live repair

Using the Keycloak Admin API, attached only the existing `basic` scope as a default scope on `arena-ui` and `arena-bff` in `arena-sit`. Preflight verified the subject mapper and both client identities before any write. A subsequent read-only check reported neither client missing the scope.

No roles, accounts, client secrets, database business records, PROD settings, service deployments or restarts were changed. Credentials and tokens stayed server-side and were not printed or committed. Existing browser tokens are not rewritten: the user must sign out and sign back in to obtain a fresh token.

## Preventing recurrence

- `deploy/keycloak_subject_scope.py`: reusable preflight/check/apply helper; validates the actual access-token subject mapper, preserves all existing scopes, attaches only missing links, and is idempotent.
- `deploy/configure-academy-keycloak.py`: reuses the helper for existing realm setup.
- `deploy/tests/test_keycloak_subject_scope.py`: read-only checks, idempotent repair, preservation of existing scopes, missing-client preflight and missing-mapper fail-closed behavior.

The helper is an explicit operator setup operation; the application startup and authorization policies are unchanged. Infrastructure bootstrap remains the authoritative full realm provisioning mechanism.

## Executed verification

`./deploy/validate.sh`: 19 tests passed, including 4 new subject-scope tests, plus shell syntax checks. Live SIT check before repair showed both clients missing basic; after repair showed an empty missing-client list. A second apply is covered by the idempotence test and performs no writes when already configured.

An authenticated customer/Owner browser retest was not performed using the user's session or credentials. Scope verification proves configuration repair; the user must verify the dashboard after signing in again. No application build or session/billing test was necessary because application code and contracts did not change.

## Rollback

Only if explicitly required, remove the two newly attached default-scope associations using the Keycloak Admin API `DELETE /admin/realms/arena-sit/clients/{clientId}/default-client-scopes/{basicScopeId}`. Do not delete the basic scope, its mapper, any other client scopes or users. Removing these associations will restore the missing-subject problem for newly issued access tokens. Existing sessions/credentials were not modified by this repair.
