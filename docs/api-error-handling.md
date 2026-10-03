# ArenaOps API error handling

## Contract

Core and login independently expose the same small contract, without a shared Maven dependency.

```json
{
  "timestamp": "2026-10-03T12:00:00Z",
  "status": 409,
  "errorCode": "CUSTOMER_ALREADY_EXISTS",
  "description": "A customer already exists with this email.",
  "path": "/api/admin/customers",
  "correlationId": "12345678-1234-1234-1234-123456789abc"
}
```

Timestamp is an ISO-8601 UTC instant. Paths exclude query strings. Validation additionally includes `reasonCode: INVALID_REQUEST_FIELDS` and a `fieldErrors` map keyed by request field. Neither module returns exception class names, rejected input values, provider response bodies, SQL, or stack traces.

A request-scoped correlation filter accepts only canonical UUID values from `X-Correlation-ID`, generates one otherwise, exposes it on the response, and puts it in MDC. The gateway forwards its validated ID to core. Gateway-normalized errors retain the outward request path and correlation ID. UUIDs are for tracing, not authorization.

## Error catalogue and HTTP mapping

| Code | HTTP status | Safe description |
|---|---|---|
| `INVALID_REQUEST` | 400 | The request is invalid. |
| `VALIDATION_FAILED` | 400 | The request contains invalid fields. |
| `AUTHENTICATION_REQUIRED` | 401 | Please sign in to continue. |
| `AUTHENTICATION_FAILED` | 401 | Your sign-in session is invalid or expired. Please sign in again. |
| `ACCESS_DENIED` | 403 | You do not have permission to perform this operation. |
| `USER_DISABLED` | 403 | Your user account is disabled. Please contact your administrator. |
| `CUSTOMER_ACCESS_BLOCKED` | 403 | Your ArenaOps account does not currently have access. Please contact your administrator. |
| `SUBSCRIPTION_EXPIRED` | 403 | Your ArenaOps subscription has expired. Please contact your administrator. |
| `SUBSCRIPTION_ACCESS_BLOCKED` | 403 | Your ArenaOps subscription is no longer active. Please contact your administrator. |
| `PAYMENT_REQUIRED` | 403 | Your trial has ended. Please contact your administrator to continue. |
| `CUSTOMER_NOT_FOUND` | 404 | Customer with the supplied identifier was not found. |
| `CUSTOMER_ALREADY_EXISTS` | 409 | A customer already exists with this email. |
| `USER_ALREADY_EXISTS` | 409 | An account already exists with these details. |
| `INVITATION_NOT_FOUND` | 404 | No invitation was found. |
| `INVITATION_INVALID` | 400 | The activation code is invalid. |
| `INVITATION_EXPIRED` | 409 | This invitation has expired. Please request a new invitation. |
| `INVITATION_ALREADY_CONSUMED` | 409 | This invitation has already been used. |
| `INVITATION_REVOKED` | 409 | This invitation has been revoked. Please request a new invitation. |
| `INVITATION_EMAIL_MISMATCH` | 400 | Registration email must match the invited email. |
| `INVITATION_NOT_READY` | 409 | This invitation is not available for registration. |
| `SUBSCRIPTION_NOT_FOUND` | 404 | No subscription was found for this customer. |
| `SUBSCRIPTION_CANCELLED` | 409 | Cancelled customers must be reactivated before recording payment. |
| `GRACE_PERIOD_ALREADY_ACTIVE` | 409 | Customer already has an active grace period. |
| `GRACE_PERIOD_NOT_ALLOWED` | 409 | Grace period can only be granted when payment is due. |
| `INVALID_GRACE_END` | 400 | Grace period end must be in the future. |
| `INVALID_STATE` | 409 | This operation is not allowed in the current state. |
| `RESOURCE_NOT_FOUND` | 404 | The requested resource was not found. |
| `CONFLICT` | 409 | The operation conflicts with existing data. Please refresh and try again. |
| `EMAIL_DISABLED` | 503 | Invitation email was not sent because email delivery is currently disabled. |
| `EMAIL_DELIVERY_FAILED` | 503 | The invitation email could not be sent. Please retry the invitation. |
| `IDENTITY_PROVIDER_UNAVAILABLE` | 503 | Sign-in services are temporarily unavailable. Please try again shortly. |
| `IDENTITY_CREATION_FAILED` | 502 | Your account could not be created in the sign-in service. Please try again. |
| `SERVICE_UNAVAILABLE` | 503 | ArenaOps is temporarily unavailable. Please try again shortly. |
| `INTERNAL_SERVER_ERROR` | 500 | An unexpected error occurred while processing the request. |
| `METHOD_NOT_ALLOWED` | 405 | This request method is not supported. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | This request format is not supported. |
| `NOT_ACCEPTABLE` | 406 | The requested response format is not supported. |

The catalogue is the same in both services so gateway forwarding preserves domain codes. A code's status is enforced when recognizing a core error; unrecognized/legacy upstream bodies become safe status-based errors. An existing `ResponseStatusException` preserves its HTTP status but its arbitrary reason is not sent to clients.

Both advice classes handle request DTO validation, parameter validation, malformed JSON, unsupported methods/media types, missing resources, authentication, authorization, typed business exceptions, and a safe unknown-exception fallback. Core also maps database integrity and locking conflicts to 409. Its known customer-email uniqueness conflict maps to CUSTOMER_ALREADY_EXISTS.

Security entry points and access-denied handlers write the same JSON contract. Customer entitlement errors from the servlet filter use that writer rather than servlet HTML/sendError. CustomerAccessFilter is registered only in the Spring Security chain, after bearer authentication, so servlet auto-registration cannot run it before authentication.

Unexpected errors log correlation ID, exception type, and bounded diagnostic frames without arbitrary messages or request/provider bodies. Email diagnostics log operation stage and nested exception types without SMTP credentials or email content. Expected business rejections do not log full error stacks.

## Onboarding and resend email outcomes

Customer creation remains HTTP 201 when customer, subscription, and invitation database writes succeed. Resend remains HTTP 200 when replacement-code preparation succeeds. These responses add:

```json
{
  "emailDelivery": {
    "status": "DISABLED",
    "errorCode": "EMAIL_DISABLED",
    "description": "Invitation email was not sent because email delivery is currently disabled."
  }
}
```

- SENT: provider/transport accepted the email; invitation becomes SENT. This is not proof of inbox delivery. No warning code.
- DISABLED: configured email-disabled implementation raises the typed EMAIL_DISABLED outcome. Invitation remains CREATED. Customer creation/code preparation succeeds and UI shows a warning.
- FAILED: provider/preparation failure becomes EMAIL_DELIVERY_FAILED. Invitation remains CREATED. UI shows a warning and exposes the prepared code for sharing/recovery.
- Business failure before email (missing customer, consumed/revoked invitation, duplicate email): structured 4xx; no success or delivery claim.
- Database/audit failure: propagates as a failed operation; it is not mislabeled as an email warning.

Creation and resend keep their existing transaction boundaries. Only the external email invocation is caught as partial success. Database saves/audit writes after accepted email are outside that catch. External email and Keycloak calls are not PostgreSQL transaction participants. A later database commit failure can occur after external email acceptance; this implementation does not claim atomic delivery or introduce an outbox/distributed transaction.

An email warning does not itself roll back onboarding. Failure audits use stable EMAIL_DISABLED/EMAIL_DELIVERY_FAILED reasons, never raw provider exception text. A resend failure does not restore the old code: replacement-code generation already succeeded. The returned new code remains the prepared invitation code. There are no automatic resend retries.

## Identity and gateway behavior

Login distinguishes invalid/expired identity exchanges (401), identity-provider unavailability (503), core unavailability (503), access denial (403), and unexpected defects (safe 500). Existing sessions survive temporary core outages. True authentication/access rejection retains existing invalidation behavior.

TokenService transport has bounded connection/read timeouts. Core registration preserves its existing database transaction and Keycloak role-assignment compensation; typed identity failures replace raw provider messages. A Keycloak call cannot be rolled back by PostgreSQL. Successful identity creation followed by a database commit failure still needs operational reconciliation.

Gateway error forwarding recognizes catalogue codes and structured validation fields from core; it discards arbitrary descriptions/bodies and builds its own safe response. Core connection failures during request proxying or retry return structured 503 rather than an empty 500/401. Cookies and browser-supplied bearer headers are not forwarded to core; the gateway uses its session token.

## Angular behavior

`api-error.ts` defines the central parser, catalogue descriptions, field-error application, HTTP interceptor, and outage notification service. The interceptor normalizes /api errors; raw HTML, arbitrary exception text, or unknown provider bodies are never displayed. It keeps a client-side `message` alias for existing component callbacks while machine decisions use `errorCode`.

A dismissible global banner covers unavailable/internal-service errors, including screens that previously swallowed failed background reads. It does not automatically retry writes or redirect through sign-in loops.

Admin onboarding/resend display SUCCESS when email is accepted, WARNING when data/code preparation succeeded but email is disabled/failed, and ERROR when the operation failed. Registration/admin forms apply server field errors and show readable messages. Login/callback use safe subscription/access messages.

The customer-email validator trims pasted email before local validation, waits 400 ms before an availability request, cancels stale checks, and blocks submission on duplicate/pending/unavailable checks. Tests reproduced the whitespace case that previously prevented the request from starting.

## Files

Both services:
- `exception/ErrorCode.java`, `ArenaOpsException.java`, `ErrorResponse.java`
- `exception/GlobalExceptionHandler.java`, `ApiErrors.java`, `CorrelationIdFilter.java`, `ApiErrorController.java`
- `config/SecurityConfig.java`

Core integration:
- `service/AdminCustomerService.java`, `AppUserService.java`, `CustomerAccessService.java`, `KeycloakService.java`
- `service/LoggingInvitationEmailService.java`, `BrevoEmailService.java`
- `model/AdminCustomerResponse.java`, `EmailDelivery.java`
- `controller/AdminCustomerController.java`, `AppUserController.java`, `AccessController.java`
- `config/CustomerAccessFilter.java`, `validation/CustomerEmailValidator.java`

Login integration:
- `controller/TokenController.java`, `service/TokenService.java`

Angular:
- `api-error.ts`, app configuration/template/styles
- admin component and customer-email validator
- registration component/template and callback component
- `scripts/test-errors.mjs` and npm test script

## Verification

Backend tests exercise both handler contracts; malformed JSON and field validation; 404/409/safe 500; servlet error fallback; correlation IDs; actual unauthenticated security-chain response; entitlement filtering and expiry/suspension distinction; invitation invalid/expired/consumed/revoked/wrong-email cases; creation/resend success, disabled email and provider failure; gateway redaction/code preservation; identity rejection/unavailability; and secret-free email diagnostics.

`npm test` runs focused Node tests against actual Angular forms, HttpErrorResponse objects, injectable notification/interceptor behavior, and transpiled project helpers. It covers safe readable errors, field-error recovery, outage banners, duplicate-email debounce/cancellation/whitespace, and retryable availability failure. It is not a browser E2E suite. The previously configured Angular test target was absent.

Final checks: `mvn package` in both backend modules, `npm test`, `npm run build`, and `git diff --check`.

## Operational limits

No live database migration, SMTP delivery, real Keycloak operation, or browser E2E test is performed by these checks. The earlier duplicate-customer-email Liquibase startup blocker is separate and remains unchanged. Resolve duplicate customer owner emails before applying that existing unique-index migration.

Trial calculation, invitation consumption, payment/grace rules, cancellation/suspension/reactivation transitions and tenant ownership remain unchanged by this error framework. Existing lifecycle duplication and paid-expiry status-display drift are separate follow-up concerns; they are not redesigned here.


### Invitation link registration

New invitation emails link to `/register#activationCode=…&email=…`. The fragment keeps the code out of HTTP request URLs and referrer headers. The form validates the invitation through the existing POST endpoint, binds the invited email, and shows only username and password inputs. Opening or validating a link does not consume it. Successful registration consumes the invitation within the existing registration transaction; reused, revoked, and expired invitations are rejected. The used fragment is removed before redirecting to login. Older emails without link parameters still support manual code entry; resend an eligible invitation to obtain the new link.
