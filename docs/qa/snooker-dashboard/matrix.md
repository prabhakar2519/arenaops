# Scenario matrix (before execution)

Every fixture scenario starts with an independent synthetic QA club, two active configured tables, one inactive table, fresh API model and browser context. Clock: 2026-11-12 12:00 Asia/Kolkata. No fixture request reaches a real API. Each scenario runs desktop/light and tablet/dark unless noted. “API negative” fixture tests verify the UI error path only, not server enforcement.

| ID | Feature / precondition | Steps | Expected | Automation |
|---|---|---|---|---|
| UI-01 | Empty dashboard | Open all six views | Correct counts and empty messages | Playwright fixture |
| UI-02 | Facility eligibility | Inspect cards | Inactive excluded, names/types correct | Playwright fixture |
| UI-03 | Guest start | Open/cancel, reopen/confirm | No POST on cancel; counts/cards change on confirm | Playwright fixture |
| UI-04 | Manual contacts | Invalid phone/name, then valid10 digits | No invalid POST; valid payload sent | Playwright fixture |
| UI-05 | Existing running session | Attempt conflicting start | Safe conflict shown; no duplicate in model | Playwright fixture |
| UI-06 | 933s running at220/hr | Open Active floor | 00:15:33 and ₹57 | Playwright fixture |
| UI-07 | Persisted running session | Advance controlled clock; reload | Timer advances, not reset | Playwright fixture |
| UI-08 | Paused session | Advance clock, resume | Frozen while paused, resumes | Playwright fixture |
| UI-09 | Stop cancellation | Quote, advance clock, cancel | Frozen quote; no stop POST/session closure | Playwright fixture |
| UI-10 | Stop settlement | Confirm with full57 payment | Table released, collected57, Recent row | Playwright fixture |
| UI-11 | Partial payment | Pay20 of57, settle cumulative57 | Unsettled37 ->0, attention clears | Playwright fixture |
| UI-12 | Unconfirmed payment | Stop with pending checkbox | No collection; Unsettled row | Playwright fixture |
| UI-13 | Quick stop | Stop with quick checkbox | No collection; table released | Playwright fixture |
| UI-14 | Expired quote API response | Confirm stale then refresh quote | Safe error, no closure; recover | Playwright fixture |
| UI-15 | Owner discount | Discount7 reason, collect50 | Approved payable50, totals consistent | Playwright fixture |
| UI-16 | Staff discount | Request7, collect50 | Pending discount, debt7 | Playwright fixture |
| UI-17 | Reservation validation | End before start | No reserve POST | Playwright fixture |
| UI-18 | Future reservation | Create slot, cancel | Reserved count/content; start disabled until window | Playwright fixture |
| UI-19 | Ready reservation | Open current window and start | Manual start transition; counts | Playwright fixture |
| UI-20 | Overlapping reservation | API409 | Useful error and no extra reservation | Playwright fixture |
| UI-21 | >20 completed sessions | Open Recent | Approved cap20; older entries in History | Playwright fixture, approved behavior |
| UI-22 | Session History | Open History | Paused Duration present; filters and pagination | Playwright fixture |
| UI-23 | QR first-time customer | Show QR, open form, submit | Name/phone/privacy; pending only | Playwright fixture |
| UI-24 | QR validation | Blank/short/invalid phone | Submission blocked | Playwright fixture |
| UI-25 | QR unavailable | Invalid ID | Safe unavailable response; no form/PII | Playwright fixture |
| UI-26 | QR cross-context refresh | Public submit; staff clock advances | List/count updates without reload | Playwright fixture |
| UI-27 | QR approval | Confirm pending customer | Exactly one start action, running row | Playwright fixture |
| UI-28 | QR rejection | Cancel waiting request | Removed, no running session | Playwright fixture |
| UI-29 | QR table occupied | Confirmation409 | Request retained, useful error | Playwright fixture |
| UI-30 | Role snapshots | Owner/full/standard/staff | Owner/Full Manager aggregates; Standard/Staff restricted | Playwright fixture, approved policy |
| UI-31 | Restricted report | Staff direct navigation | Redirect without collections API | Playwright fixture |
| UI-32 | Dashboard outage | API503 then retry | Readable error, refresh recovery | Playwright fixture |
| UI-33 | Desktop/tablet | Switch tabs/dialog in both projects | No document overflow | Playwright fixture |
| API-* | Real lifecycle/billing/tenant/security | Run existing Core checkout/public-club/session/facility tests | Persistence, locks, monetary invariants, no duplicate operations | Real Spring/H2 tests |
| LIVE-* | Supplied role BFF storage state | Read real dashboard/authority/collections | Role and financial policy; no API mocking | Opt-in Playwright live |
| BLOCKED-* | No sanctioned live seed/cleanup | Start/stop/payment/QR live mutations | Must not run on shared data | Blocked until isolated test tenant/setup |

Execution results are in `execution.md` and machine-readable `arena-ui/test-results/results.json`. Actual Playwright titles contain the IDs. Source-only findings are not counted as executed passes.
