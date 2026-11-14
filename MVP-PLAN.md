# Academy Setup MVP v1.0 — gap analysis and plan

Branch: feature/academy-setup-mvp-v1. Functional baseline: the supplied specification; Confluence is not connected in this session.

Reuse customer as organization, app_user as authenticated account, academy_staff as optional membership/personnel, existing academy sports, staff/sport and batch/coach links, existing schedules, lifecycle enforcement, audit/errors, and polished UI components. Existing legacy parlour tables represent venues, not individual academy play areas; no operational Play Areas controller or entity exists in this checkout.

Implement additive migrations for setup confirmations/evaluation, multi-role membership and identity lifecycle, tenant play areas, and a concurrency-safe single active owner constraint. Keep existing owner registration/license activation and subscription logic.

Implement database-derived applicable-step progress and review; owner dashboard landing; per-sport capability model; optional owner-managed coaching; manager business-record administration; instructor assignment-scoped reads; owner-only login and application-role operations. Keycloak will own expiring required-action links and credential storage; its branded email theme uses Brevo SMTP (user-approved). Persist provisioning/delivery/activation state and identity subject, reconcile completed setup at authenticated access, and reject disabled/revoked membership even with an old JWT.

Complete dashboard/checklist/profile/review, staff designation/roles/access controls, facilities, responsive navigation and validations. Validate frontend, backend and disposable PostgreSQL with focused security and lifecycle tests; leave SIT/production undeployed.
