# PlexonQuests 4.1 Full Revamp Audit

Status: implementation baseline

Baseline: `main` at `57a235460e5e936ca931ee8d0e96b0a424f134f8` (`4.0.0` stable repository closure)

Target runtime: Paper `26.2.build.121-stable`, Java 25, PlexonCore 2.0.4.

This audit applies the Plexon Plugin Full Revamp Standard to the current stable source. It intentionally treats the accepted 4.0.0 Phase 2/Phase 3 architecture as a protected baseline: existing reward, persistence, progress, provenance, prerequisite, rotation, and journal semantics are retained unless a concrete defect or measurable simplification justifies change.

## Product jobs

PlexonQuests currently has five primary jobs:

1. assign and rotate Daily, Weekly, Milestone, and staff-assigned quests;
2. track authoritative objective progress without rewarding invalid or duplicated activity;
3. present a player-facing Quest Journal for discovery, progress, tracking, rerolls, claims, and history;
4. deliver rewards safely and persist progress/history across restarts;
5. expose administration, diagnostics, API, PlaceholderAPI, PlexonCore, and Plexon Family integration surfaces.

These jobs remain valid. The 4.1 revamp is therefore an architecture/safety consolidation rather than a product reset.

## Architecture map

### Lifecycle and composition

`PlexonQuestsPlugin` owns startup/shutdown, service composition, listener registration, scheduled maintenance, runtime configuration refresh, API registration, and optional integration registration.

### Domain/application services

- `AssignmentService`: assignment mutation authority.
- `ProgressService`: objective contribution/progress authority.
- `QuestEligibilityService` + `QuestPrerequisiteService`: eligibility and prerequisite rules.
- `RotationService` + `RerollService`: rotating quest lifecycle and reroll transactions.
- `RewardService`: reward claim authority.
- `QuestTrackingService`: tracked-quest state.
- `CompletionHistoryCache`: completion-history cache/loading.
- `BlockOriginService`: local provenance support when PlexonCore is not authoritative.

### Persistence

`StorageService` owns SQLite persistence, WAL/checkpoint/maintenance behavior, asynchronous storage operations, history queries, and progress/profile writes. `AssignmentSnapshotCodec` owns assignment serialization compatibility.

### Runtime/event paths

- `CoreObjectiveListener` routes supported gameplay events into objective progress.
- `ActivitySampler` handles sampled movement/playtime-style progress.
- PlexonCore runtime integration can become the authoritative acquisition/provenance source while the local path remains a compatibility fallback.

### Player UI

There are currently two inventory GUI families:

1. `Phase2JournalService`: the accepted 4.x player-facing journal (`HOME`, `ACTIVE`, `ELIGIBLE`, `COMPLETED`, `TRACKED`, `HELP`, details, reroll confirmation). It owns presentation/navigation only and delegates mutations to authoritative services.
2. `MenuService` + `MenuListener` + `QuestMenuHolder`: the older configurable menu family that remains reachable through compatibility/admin paths.

Normal 4.x player command routing in `Phase2QuestCommand` and the public API journal opener already use `Phase2JournalService`. The older stack must therefore not be deleted blindly, but its remaining responsibilities should be narrowed and documented.

## Feature viability matrix

| Feature | Player value | Frequency | Runtime / exploit risk | Decision |
| --- | --- | --- | --- | --- |
| Unified Quest Journal | High | High | Medium interaction risk | KEEP + HARDEN |
| Daily/Weekly rotation | High | High | Medium scheduling/state risk | KEEP |
| Milestone quests | High | Medium | Medium state risk | KEEP |
| Staff/manual assignments | Medium | Low | Medium admin mutation risk | KEEP |
| Quest tracking | High | High | Low/medium stale-view risk | KEEP |
| Reward claiming | High | Medium | High transaction risk | KEEP authoritative service |
| Rerolls | Medium/high | Medium | High economy/state risk | KEEP with confirmation/guards |
| Completed history | Medium | Medium | Storage latency risk | KEEP async paging |
| Eligibility/prerequisites | High | High | Logic complexity | KEEP |
| PlexonCore provenance/runtime | High | High | High correctness risk | KEEP authoritative integration |
| Local provenance fallback | Medium | Runtime-dependent | High correctness/perf risk | KEEP compatibility fallback |
| PlaceholderAPI expansion | Medium | Medium | Low/medium hot-placeholder risk | KEEP |
| Legacy configurable player journal path | Low after Phase 3 | Low | Duplicate maintenance surface | REDUCE / compatibility only |
| Admin diagnostics/config surfaces | High for operators | Low | Low | KEEP |
| Decorative GUI animation | Low | N/A | Avoidable task/allocation cost | DO NOT ADD |

## Confirmed strengths to preserve

- Custom `InventoryHolder` identity is already used; menu identity is not based on inventory titles.
- Both current GUI families cancel drag interactions.
- The legacy listener explicitly rejects several unsafe click types and has configurable per-player debounce.
- The premium journal has its own short debounce and operation submission guards for claim/tracking/reroll actions.
- State-changing journal actions call existing authoritative services rather than mutating balances/progress from rendered icons.
- Completed-history loading is asynchronous and verifies the exact currently open holder before rendering results.
- The journal adds no repeating GUI refresh task.
- Accepted Phase 3 navigation context, stale-state messaging, empty states, tracking-in-place, and final control-bar geometry are covered by architecture tests.
- Maven already targets Paper 26.2 / Java 25 and CI proves accepted Phase 3/performance ancestry before packaging.

## Confirmed gaps

### P0 — inventory actions run directly inside `InventoryClickEvent`

Both `MenuListener` and `Phase2JournalService` dispatch slot actions directly from their click handlers. Many of those actions open, replace, or close inventories. Paper inventory transitions should be deferred to a safe subsequent scheduler execution point rather than performed directly inside `InventoryClickEvent`.

4.1 requirement:

- cancel the originating event immediately;
- accept only supported top-inventory action clicks;
- capture the action and click intent;
- schedule action execution for the next primary-thread task;
- before execution, confirm the player remains online and the exact same holder is still open;
- then execute the action, whose authoritative service must still revalidate mutable state.

### P0 — journal click policy is less strict than legacy policy

`Phase2JournalService` currently dispatches an action for any click type that lands on an actionable top slot after event cancellation. This means number-key, off-hand, drop, double-click, creative/middle, and shift variants can still trigger the semantic action even though item movement is cancelled.

4.1 requirement: player journal actions accept ordinary left/right clicks only unless a future action explicitly documents another click contract. Shift/hotbar/off-hand/drop/double-click/creative/unknown paths remain cancelled without semantic dispatch.

### P1 — duplicated GUI event routing

`MenuListener` and `Phase2JournalService` each own click/drag routing and debounce policy. The divergence above proves that duplicated routing can produce inconsistent safety behavior.

4.1 direction: converge on one internal interaction policy/router contract. Full physical merger can be incremental so the stable journal is not destabilized merely for code aesthetics.

### P1 — large UI coordinators

`MenuService` and `Phase2JournalService` combine rendering, navigation, slot binding, state presentation, and orchestration in very large classes. They are functional but expensive to reason about and regression-test.

4.1 direction: extract small, behavior-preserving components only where they reduce duplicated logic: interaction policy, control-bar rendering, item templates, and journal view renderers. Do not rewrite quest services.

### P1 — runtime scheduler abstraction is incomplete

The project correctly uses sync/async Bukkit scheduling for current Paper operation, but scheduler calls are spread across services. This keeps Folia support out of scope today and makes future scheduler-model migration more expensive.

4.1 direction: introduce a small scheduler adapter only after current Paper behavior is covered by tests. Do not declare Folia support until the runtime model is actually implemented and tested.

### P2 — command implementation remains Bukkit command executor based

The command UX is mature and permission-aware, but it does not yet use Paper's lifecycle/Brigadier command registration. This is an evaluation item rather than an automatic rewrite because the current command compatibility surface is extensive.

Decision: retain for 4.1 unless Brigadier migration demonstrably improves completions/discoverability without breaking API/admin command compatibility.

## 4.1 implementation slices

### Slice A — interaction safety

- Harden both GUI families to defer actions outside `InventoryClickEvent`.
- Normalize supported click policy.
- Preserve cancellation of all movement/drag paths.
- Add regression/architecture tests for deferred execution and unsupported-click rejection.

### Slice B — interaction architecture consolidation

- Introduce a shared internal interaction-policy abstraction used by both menu families.
- Keep holder-local slot action maps and authoritative domain services.
- Preserve the Phase 3 journal's exact navigation semantics and accepted slot geometry.

### Slice C — maintainability extraction

- Split journal rendering into cohesive view renderers only when tests prove parity.
- Keep one explicit navigation context and stale-view validation model.
- Cache only stable display artifacts; avoid live animations and periodic menu redraws.

### Slice D — verification

Required before a 4.1 candidate is considered release-ready:

- clean Maven `verify` on Java 25;
- existing lineage/build/package checks remain green;
- GUI safety regression tests cover left/right, shift, number key, off-hand, double click, drag, bottom-inventory movement attempts, close/reopen, and stale scheduled action;
- reward/reroll/tracking transaction tests remain green;
- startup smoke test on Paper 26.2;
- restart/persistence smoke test;
- active-use spark comparison for objective tracking and repeated journal interaction;
- rollback remains stable `4.0.0` until 4.1 runtime certification is accepted.

## Non-goals

- No quest/reward economy rebalance in this architecture pass.
- No schema rewrite without a proven persistence need.
- No resource-pack dependency.
- No decorative repeating GUI animation.
- No claim of Folia support.
- No removal of compatibility APIs solely to reduce file count.

## Release gate

This document is an implementation baseline, not release evidence. A 4.1 release must still satisfy build/test/runtime/persistence/performance gates and publish a reproducible artifact with exact source SHA and rollback information.
