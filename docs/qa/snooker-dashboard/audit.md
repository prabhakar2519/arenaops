# Snooker dashboard functional audit

Audit date: 2026-11-12. Scope: current repository; no application functionality changed for this audit. This report distinguishes source inspection, browser tests with controlled API fixtures, real Core/H2 service tests, and live-stack verification. A fixture pass is not a backend security or persistence pass.

## Implementation inventory

| Feature | Classification | Implementation and findings |
|---|---|---|
| Snapshot counts | Implemented; execution pending | `RunningSessionService.dashboard` computes eligibility/occupancy; Angular `available`/`running` getters. Paused sessions remain STARTED and count as running. Current reservations exclude a facility only during their window. |
| Live tariff | Implemented | `RunningSessionService.amount`, `billableSeconds`; Angular `amount`, `duration`. Seconds minus accumulated/open pause; hourly snapshot. BigDecimal divide by 3600 to 8 decimals HALF_UP, then 2 decimals HALF_UP, then DOWN to whole rupee for Snooker. ₹220 × 933s yields ₹57. No completed-minute rounding. |
| Collected today | Implemented | `SessionCheckoutService.collectedToday`, `collections`; `RunningSessionService.dashboard` sums actual dated AcademyPayment records. Bills and pending discounts are not cash receipts. |
| Owner-only financial access | Defective against requested policy | `SessionCheckoutService.financial` delegates to `Actor.operations`: Owner **and Full Operations Manager**. Both can read snapshot revenue and collections. UI uses nonnull API fields and `canViewFinancials`, so follows this policy. Standard Manager/Staff lack aggregate totals. Do not silently change permissions. |
| Per-session financial exposure | Policy discrepancy | `RunningSessionService.redact` permits session starter/stopper as well as financial operators. Active sessions and configured facility rates are returned by dashboard. Angular calculates live session amounts from those rates. Current permission model allows operational pricing; strict Owner-only interpretation would need an approved coordinated API/UI change. |
| Attention | Implemented; empty state partial | `DashboardInformationService.alerts`: unsettled, unconfigured facilities, reservations due within 30m, provisional attendance, missing coaches, student dues, discount approvals. Role-gated to Owner/Manager. Click unsettled -> `view=UNSETTLED`. No alerts hides the section rather than showing an explicit empty message. |
| Dashboard tabs | Implemented | Available facilities, Active floor, Reserved sessions, Unsettled sessions, Recent sessions, Customer check-ins. Query-param selection supported. No separate Scheduled Sessions tab. Training/attendance schedules are distinct academy functionality. |
| Facility cards | Implemented | Configured/active/capable/positive-rate/authorized facilities; available getter excludes unavailable entries. Name/code/type/rate, manual Start, Reserve, Show QR. No changes made. |
| Manual Start | Implemented | `Start` DTO, `RunningSessionService.start`, Angular `openStart/start`. Guest path permits no contact details; supplied mobile requires name; optional +91 for valid Indian numbers. Planned end input; no fixed-duration tariff selector in this flow. Only confirmation POST starts; no auto-start from selections. |
| Duplicate start | Implemented | Facility lock + active-session lookup + reservation overlap checks. PostgreSQL active-session constraint exists in session migrations; concurrency evidence must come from real backend tests, not mocked browser routing. |
| Timers, pause/resume | Implemented | Local 1s ticker; persisted timestamps and `totalPausedSeconds`; open pauses stop timer/billing. Stopping paused applies final open pause at quote capture. Reload uses persisted data. Historic missing pause data cannot be recovered reliably. |
| Planned duration limit | Partially implemented / no hard stop | Planned end protects reservation windows. No fixed-duration package billing, visible countdown, or scheduled automatic session termination in the Snooker dashboard. Do not invent these as current requirements. |
| Stop/discount/payment | Implemented | Frozen actor/version-bound expiring quote, cancel/revoke, billing at quote capture, partial payments, requested vs approved discount, cancellation under 60 billable seconds unless Quick Stop. `stoppedAt` is confirmation time, `endedAt` quoted cutoff. |
| Settlement | Implemented | `settle` uses cumulative collected total, not an additional payment amount. `collect` records only positive delta, never negative/overpayment. Duplicate closure invalid-state; repeated cumulative amounts cannot double collect. |
| Reservations | Implemented | Explicit reserve/start/cancel. Reserved start permitted only inside window, locks/checks availability and snapshots current rate. Expired reservations are cancelled during dashboard read. No automatic start. |
| Recent Sessions | Policy discrepancy | Core selects latest **100** STOPPED/UNCONFIRMED/CANCELLED ordered by **start**, ID descending, across all dates. UI excludes unsettled and shows remaining rows without a 10-row cap. Therefore not necessarily the newest completion first. No pagination changes made. |
| Session History | Implemented | Date/player/status filters, 25-row pagination, Paused Duration HH:mm:ss. Separate from dashboard Recent Sessions. |
| QR check-in | Implemented | Permanent revocable facility ID; anonymous name/phone/privacy form, five-minute single-use form nonce, two-second anti-bot age, two-minute pending request, one pending/facility; authorized staff confirms via locked lifecycle. No PIN/OTP or mobile-only lookup of historical customer data. Customer directory linked only after successful start. |
| QR realtime visibility | Implemented with polling | Independent 2-second list poll; no push/SSE/WebSocket. No overlapping requests; teardown on route exit. UI updates list/count, preserves current tab. Background browser throttling/network delays prevent an absolute 2s guarantee. |
| QR hosting | Environment prerequisite | DEV uses current local/LAN path + slug; SIT shared preview requires explicitly enabled signed preview routing; production club-subdomain DNS/TLS required. Unknown/unpublished slug, bad signed host, invalid/mismatched QR rejected. No infrastructure modified. |
| Customer validation | Implemented | Bare ten-digit Indian mobile -> canonical +91; +91 national digits start 6–9. Explicit non-Indian international syntax accepted, not country-specific number-plan verification or phone reachability. Names required for QR, optional manual guest. |

## Gaps and approval boundary

1. **D-01, high: financial policy differs from Owner-only requirement.** Reproduce with Full Operations Manager: GET `/api/academy/running-sessions/authority` returns `canViewFinancials=true`; dashboard returns financial aggregates, collections allowed. Source proof: `Actor.operations`, `SessionCheckoutService.financial`. Existing role model intentionally grants this; obtain approval before changing it.
2. **D-02, medium: Recent list differs from requested default ten/newest completion ordering.** Seed >10 closed records in an isolated fixture; open Recent sessions. Actual: all supplied closed entries after unsettled filtering; backend limit100, start-time order. Requirement explicitly says report before changing. No code change.
3. **G-01: no explicit Attention empty state.** Section disappears when no alerts. Optional UI improvement, not an invented alert.
4. **G-02: no fixed-duration package/countdown/auto-stop flow.** Planned end is reservation planning, not a package tariff. Out of scope until specified.
5. **Automation limitation:** no sanctioned isolated live-tenant provisioning/cleanup API or test-role credentials found. Live mutations must remain blocked. Never borrow a logged-in user's cookies or alter real tables for test cleanup.
6. **Financial security caveat:** a mocked `/api/user` or mocked 403 does not prove server authorization. The suite labels these as UI contract tests. Actual role/tenant/transaction checks use Core tests, and live role tests require externally supplied BFF storage states.

## Requested approvals after reporting

Approve a specific Owner-only aggregate/report policy and clarify operational per-session tariff visibility separately before changing authorization. Confirm whether Recent Sessions should be capped at10 and sorted by completion time. No business/UI changes are made by this audit.

## Confirmed scope update

The user confirmed Full Operations Managers retain full financial access. D-01 is therefore resolved as an approved policy, not an authorization defect. Owner and Full Operations Manager may read aggregates/reports; Standard Manager and Staff remain restricted under the existing model. The user also approved a 20-session Recent Sessions display limit. Angular applies this cap after excluding unsettled sessions; Session History remains available for older entries. Core query limit100 and start-time ordering are unchanged. D-02 is resolved by this approved display change. No other financial, tariff or lifecycle changes are authorized by this clarification.

## User clarification (2026-11-12)

The user approved full access for Full Operations Manager and requested20 Recent Sessions. D-01 is therefore resolved as a requirement clarification: existing Owner/Full Operations Manager aggregates and reports are correct. Operational per-session access follows the existing role policy. D-02 is approved for a minimal UI change: the `history` getter filters unsettled rows then slices20, retaining backend100 candidates and full Session History. Start-time ordering is preserved. No additional business changes are authorized or made by this audit. Final test expectations use this approved policy and cap20.
