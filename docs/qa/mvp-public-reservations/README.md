# Public reservation requests deferred from MVP

Public reservation requests are retained in source but disabled by default.

- Frontend release switch: `arena-ui/src/app/features.ts`, `MVP_FEATURES.publicReservationRequests=false`.
- Dashboard link, direct inbox route, public reservation form and website reservation settings are hidden/guarded.
- Core switch: `arena.public.reservation-requests-enabled`, default **false** in both the service gate and public site projection.
- Core refuses public submissions, inbox reads and approval/rejection calls with `RESOURCE_NOT_FOUND` (404). Existing BFF forwarding preserves the safe error. Authentication and validation can still reject a request earlier; no disabled operation executes.
- Public site and Owner preview return `bookingsEnabled=false` while disabled, even if an academy previously enabled it in stored settings.
- Existing settings and request rows remain stored. No migration, deletion or status conversion is performed.
- Staff-created reservations in Reserved Sessions, manual session start, QR customer check-in/approval, customer directory and public site publishing remain available.

Blocked API operations:

- POST `/api/public/club/reservations`
- GET `/api/academy/public-reservations`
- POST `/api/academy/public-reservations/{id}/decision`

To release later, deliberately enable the frontend release switch and the Core property together, then rerun the retained reservation tests. UI/academy settings alone cannot enable the backend operations. Apply the Core default by deploying/restarting the updated service; reload/rebuild Angular for the hidden UI. No running service or production infrastructure was changed during implementation.

Tests exercising the retained feature explicitly opt into it in their test configuration; separate tests assert disabled defaults and the actual HTTP404 behavior, no request mutation/created sessions, preserved settings and working QR/staff reservations.

Verification completed: 6 Playwright cases passed in desktop/light and tablet/dark (hidden dashboard link, blocked direct route/no inbox fetch, public form hidden despite stale enabled metadata, publishing controls remain). Existing187 frontend tests passed with retained future-enabled feature cases explicitly opted in. Core PublicClubsIntegrationTest passed, including HTTP404 checks for all three disabled operations, unchanged stored configuration/pending status, working QR inspection and staff-created reservation, plus a disabled-by-default service test. Angular build, E2E TypeScript check, API source-review validation and diff checks passed. Tests used synthetic browser data and isolated H2 persistence.
