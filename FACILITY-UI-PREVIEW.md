# SPMS facility UI reuse — frontend preview

Historical report: superseded by [Facilities & Resources implementation](FACILITIES-RESOURCES.md). The in-memory preview has been replaced by the persistent module.

## Source inspected

SPMS is available at `/home/prabhu/Prabhu/Projects/SPMS`.

Frontend reused/adapted:

- `spms-ui/src/app/manage-tables/manage-tables.component.ts`: shared table/court experience, quantity FormArray, game types, hourly-rate defaults, total counts and prefix/continuing-number behavior.
- `spms-ui/src/app/manage-tables/manage-tables.component.html`: centred heading, sport switcher, type/count/rate cards, existing-facility list and sticky summary/save footer.
- `spms-ui/src/app/manage-tables/manage-tables.component.scss`: card grid, list cards, edit-field styling, responsive layout and sticky footer, mapped to ArenaOps green/light/dark theme tokens.

No SPMS HTTP service, authentication, authorization, backend code or migration was copied. The legacy component directly used HttpClient, so there was no isolated facility service/model suitable for reuse without carrying the old API contract.

## Screens and navigation

- `/academy/facilities` now renders the frontend-only FacilityPreviewComponent rather than the old backend-backed generic Play Areas screen. The old component/backend implementation remains untouched; it is not used by this route for this review stage.
- Facilities & Resources remains a single sidebar item and opens the general preview. It is available to Owners and Managers in academies and single-sport clubs. No sport-specific sidebar sections were introduced.
- Sport Configuration shows **Manage Facilities** for supported active activities with Play & Facility Access saved and still enabled in the current draft. It passes `sportId` and `sportCode` to the preview.
- Supported previews: Snooker, Badminton and the existing Pool/Billiards catalogue entry. Unknown codes and ID-only contextual links show an explanation rather than silently treating another sport as Snooker. Reopen old ID-only links from the updated configuration screen.
- The page uses existing authenticated business branding for its workspace name. It does not fetch facility data, verify capabilities through a new API or grant operational access.
- Existing Owner/Manager route guards are retained. Route-leave and query-context changes run the unsaved-change guard. Sport switching and Cancel also ask before discarding drafts; browser reload warns for unsaved changes.

## Preview behavior

- Snooker keeps SPMS English, French, Pool and Semi-English types with E/F/P/SE prefixes. Badminton uses Court and C prefixes. Pool/Billiards opens Pool only.
- Enter quantities/default hourly rates and Generate to create individually editable cards. English 3, French 2, Pool 2 produces E1–E3, F1–F2 and P1–P2; six badminton courts produce C1–C6.
- Further generation continues after the highest existing preview number. Existing draft values and independently edited prices are preserved.
- Per facility: generated read-only code, optional display name, hourly rate and an optional table frame-rate field. Badminton retains hourly pricing only.
- Save Preview validates every row before replacing the in-memory snapshot. Invalid rows retain all inputs and leave the previous complete snapshot unchanged. Pending ungenerated quantities must be generated or cancelled first.
- Cancel restores the last saved preview for the selected context. Type/sport switches keep separate snapshots within the same component instance. No localStorage/sessionStorage/IndexedDB, server requests or durable persistence are used.
- Clear notices explain that this is illustrative, memory-only, cleared when leaving the page/refreshing, and does not complete Academy Setup. Save never claims a server operation succeeded.
- Numeric fields reject negative, non-finite and more-than-two-decimal pricing. Names are optional and capped at 160 characters. Preview-only guardrails: 50 generated items per type at a time and 200 per sport. These are not proposed backend limits. Zero pricing is permitted for illustration and explicitly marked as not an approved free-use rule.

## SPMS backend and schema findings — read only

Inspected under `spms-core/src/main/java/com/arena/core`:

- `controller/GameTableController.java`
- `controller/RateCardController.java`
- `service/GameTableService.java`
- `entity/GameTableEntity.java`
- `entity/RateCardEntity.java`
- `model/GameTableRequest.java`, `GameTableRegisterRequest.java`, and related setup request references.

Relevant legacy endpoints include GET/POST `/api/game-tables/parlour/{parlourId}`, POST `/api/game-tables`, DELETE `/api/game-tables/{tableId}/parlour/{parlourId}`, and GET `/api/rate-cards/parlour/{parlourId}`. None is called by the preview.

Schema sources inspected: `spms-core/src/main/resources/db/changelog/features/R1_0_tables/02-create-game-table.xml`, `04-create-rate-card.xml`, and the sport-column migration location `R1_5_alter/01-add-sport-columns.xml`. SPMS also has duplicated base migration sources in `spms-db-migration`; neither set was copied or executed.

Legacy `game_table` links a parlour to a table number, game type, sport, active flag and timestamps. `rate_card` links parlour/game type/sport to hourly price, effective time and active state. Rate replacement deactivates the previous type-wide rate. Registration checks user/parlour association and duplicate table numbers for the sport; deletion checks active sessions.

Important: the apparent per-table Edit in SPMS actually updates a **game-type rate card**, changing the rate shared by matching tables. The legacy model inspected does not provide the requested independent table rates, display names or frame rates. These extensions are mock UI fields only, not a migrated backend model.

## Product/design gaps for review

1. Approve per-facility pricing versus legacy shared type pricing, including how any defaults/overrides should work.
2. Decide frame-pricing applicability by table type, particularly French and Pool, and rounding/currency/zero-rate rules. Optional frame fields here only demonstrate the requested UI.
3. Confirm whether Semi-English remains supported; it is retained to preserve the legacy experience.
4. Confirm facility code editability, uniqueness scope, quantity limits and handling of existing records.
5. Decide eventual facility lifecycle/dependency checks and explicit migration from existing ArenaOps Play Areas. No delete action, booking, timing or billing workflow was added to this preview.
6. Decide how future persisted facilities contribute to Academy Setup. The preview deliberately makes no setup updates.

## Authorization and validation required before backend integration

The URL and local mock role checks are not server authorization. A future backend must derive tenant/account identity from authentication; enforce role and active membership permissions, registered sport ownership and saved active capability; validate supported types and all prices; atomically generate unique codes and save all facilities; reject stale writes/concurrent duplicates; audit changes; and protect operational commitments before destructive changes. Query parameters must never authorize access or choose another tenant. No such API changes were made in this task.

## Files created/modified

Created:

- `arena-ui/src/app/facilities/facility-preview.component.ts`
- `arena-ui/src/app/facilities/facility-preview.component.html`
- `arena-ui/src/app/facilities/facility-preview.component.scss`
- `arena-ui/src/app/facilities/facility-preview.store.ts`
- `arena-ui/scripts/test-facility-preview.mjs`
- This report.

Modified:

- `arena-ui/src/app/app.routes.ts`
- `arena-ui/src/app/sidebar/sidebar.component.ts`
- `arena-ui/src/app/training/sport-configuration.component.ts`
- `arena-ui/package.json`
- `arena-ui/scripts/test-sports-activities.mjs`
- `arena-ui/scripts/test-business-profile.mjs`

Existing uncommitted work was preserved on `feature/sports-activities-mvp-v1`. No backend, database, infrastructure or Keycloak file was edited for this task.

## Verification and compatibility

- `npm test` from `arena-ui`: **100 passed**, zero failures, including seven new facility-preview tests.
- `npm run build`: **passed**, including Angular template compilation.
- `git diff --check`: **passed**.
- Preview tests cover E/F/P and C generation, continued numbering, independent rates, all-or-nothing validation, draft preservation/cancel, isolated snapshots and restricted roles.

The UI retains the SPMS layout, adapted to the existing ArenaOps tokens. Type cards wrap, individual rows become full-width cards, sport choices wrap, and the sticky action footer stacks on small screens. Generated input labels and inline errors are linked with IDs/ARIA attributes. Actual device/browser rendering, soft-keyboard interaction and keyboard focus remain manual verification items; no server/browser session was started.

No services/containers were started, stopped or restarted. No migration, deployment, commit, push or merge was performed. Backend integration stops here pending UI/product review.
