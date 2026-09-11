# PlexonQuests 4.0.0 — Phase 2 Premium Rebuild Execution Contract

Status: implementation contract for `phase2/4.0.0-premium-journal`

Baseline: PlexonQuests 3.3.1 (`b74fc212d5aea41a0e01e9bc1bf5c5382302d014`)

Target: PlexonQuests 4.0.0 release candidate. Stable promotion remains runtime-gated.

## 1. Source-grounded baseline

The 3.3.1 codebase already has mature infrastructure that must be preserved:

- Java 25 / Paper 26.2 Maven build;
- PlexonCore 2.0.4 as a provided dependency;
- shared Core Runtime block acquisition and origin/provenance handling;
- indexed per-player objective interest with material/entity fast gates;
- durable contribution-token deduplication;
- bounded single-writer SQLite persistence with WAL and schema migrations;
- deterministic assignment state transitions;
- rotating daily/weekly pools and milestones;
- claim reservation, preflight, rollback and uncertain-transaction handling;
- exact serialized ItemStack restoration for custom reward items;
- Vault economy and LuckPerms reward bridges;
- async history/storage access with Bukkit mutations marshalled to the primary thread;
- immutable configuration snapshots and known-good reload rollback;
- existing public API, Bukkit events and PlaceholderAPI integration.

These systems are not rewrite targets unless an implementation-level defect is found.

## 2. 4.0.0 product boundary

4.0.0 is justified by a player-facing and authoring contract expansion rather than by replacement of the runtime engine.

The player product becomes a quest journal organized around:

`DISCOVER -> TRACK -> PROGRESS -> REWARD PREVIEW -> COMPLETE -> HISTORY`

The upgrade adds:

- explicit journal/discovery states derived from definitions plus durable assignment state;
- prerequisite-aware quest definitions and validated dependency graphs;
- an Overview/Active/Available/Categories/Tracked/Completed/Statistics/Help navigation model;
- backwards-compatible `pinned` semantics exposed as `tracked` in the 4.x player product;
- richer immutable API views and PlaceholderAPI aliases;
- staged, expiring, target-bound destructive admin confirmations;
- publication-readiness diagnostics;
- migration/readiness documentation and regression contracts.

## 3. Compatibility rules

### 3.1 Durable assignment state

Keep the persisted `AssignmentState` machine intact:

- ACTIVE
- COMPLETED
- CLAIMING
- CLAIMED
- EXPIRED
- CANCELLED

Do not persist the new journal states in place of these values.

### 3.2 Derived journal state

Expose a separate immutable/derived state model:

- LOCKED
- AVAILABLE
- ACTIVE
- TRACKED
- COMPLETABLE
- COMPLETED
- COOLDOWN
- EXPIRED
- DISABLED

Derivation must be deterministic and side-effect free. `TRACKED` is the 4.x product name for the existing single pinned assignment; existing `pin`/`unpin` API and placeholders remain supported.

### 3.3 Quest acceptance

Preserve existing architecture:

- daily/weekly quests are rotation-assigned;
- milestone quests are lifecycle-assigned when eligible;
- manual quests remain explicitly admin-assigned.

Do not add a second acceptance system that can duplicate these authorities.

## 4. Prerequisite graph

Quest YAML may add:

```yaml
prerequisites:
  completed-quests:
    - first-steps
```

Rules:

- IDs use the existing identifier contract;
- duplicate prerequisite IDs are normalized away;
- self-reference is invalid;
- missing referenced quests are invalid;
- dependency cycles are invalid;
- a quest depending on a disabled prerequisite is diagnosed as unreachable;
- graph errors block activation of the candidate registry; the previous known-good registry stays active on reload.

Prerequisite completion checks must use profile-cached completion metadata. Do not query SQLite during GUI rendering, objective handling, placeholder resolution or eligibility evaluation.

## 5. Persistence and 3.3.1 migration

Keep SQLite as the authoritative persistence layer.

Add an idempotent schema migration for cached completion identity if required by the implementation. The migration must:

- preserve every existing 3.3.1 table and row;
- derive completion identity from existing claimed/history rows when possible;
- be safe to rerun;
- run through the existing serialized writer;
- preserve assignment definition snapshots and claim transaction identities;
- never silently wipe malformed state.

The existing backup command remains the rollback boundary. Migration documentation must instruct operators to take a backup before first 4.0.0 startup.

## 6. Journal UX

The premium 4.x journal must provide concise screens for:

- Overview;
- Active Quests;
- Available Quests;
- Categories;
- Tracked Quest;
- Completed / History;
- Statistics;
- Help.

Quest cards should remain concise and show, where applicable:

- name;
- category/scope;
- derived state;
- objective summary;
- progress;
- reward summary;
- completion/readiness;
- interaction hint.

Quest details retain objective and reward previews and add prerequisite/tracking context without creating lore walls.

Existing `/quests daily`, `/quests weekly`, `/quests milestones`, `/quests history`, `/quests pinned`, claim/reroll/settings and admin paths remain compatible.

Add player aliases/navigation paths such as `/quests overview`, `/quests active`, `/quests available`, `/quests categories`, `/quests tracked`, `/quests statistics`, and `/quests help` where they fit the implemented menu surface.

## 7. Objective/provenance contract

Preserve all currently supported objective types and the current `PlayerObjectiveIndex` interest model.

Do not scan every quest for every event.

For block objectives, preserve Core Runtime single-authority semantics and conservative provenance:

- NATURAL: eligible when the objective requests natural origin;
- PLAYER_PLACED: not eligible for natural-only objectives;
- UNKNOWN: fail closed unless the existing configuration explicitly permits it.

Do not perform SQLite/YAML lookups in block event hot paths.

PlexonSpawners 3.x remains runtime-optional while its Phase 2 build is an RC. Any provenance bridge must fail safely when the API is absent or incompatible.

## 8. Reward transaction contract

Retain the 3.3.1 transaction design:

`preflight -> CLAIMING -> durable reservation -> reversible delivery -> irreversible boundary -> durable completion`

Continue to reject duplicate live claim transactions per assignment.

Exact serialized ItemStack reward payloads must survive unchanged.

Inventory-full behavior continues to follow the configured overflow policy and must never silently discard custom rewards.

No new reward integration is added solely for feature count.

## 9. Admin safety

Destructive commands such as force-complete and reset require a staged confirmation token/state before mutation.

Confirmation records must be:

- short-lived;
- actor-bound;
- action-bound;
- target-bound;
- assignment/scope-bound;
- stale-state checked immediately before mutation;
- consumed exactly once.

Console use must be supported without weakening the binding contract.

Audit entries remain authoritative after the mutation succeeds.

## 10. Async and performance

Keep these invariants:

- no live Bukkit/Paper object access from storage/config worker threads;
- immutable/primitives-only async DTOs;
- no task per player, quest, objective or tracked quest;
- no DB query per contribution event;
- no synchronous DB history scan from menu open;
- no eager lore rebuild every tick;
- bounded storage/config work;
- main-thread inventory, GUI and Bukkit mutation only.

Tracked-quest feedback reuses the existing shared presentation/effect scheduling model rather than creating per-player schedulers.

## 11. Public API and placeholders

Preserve the 3.x API methods and add immutable 4.x views for journal state/prerequisites as additive API methods or records.

Keep `pin`/`unpin` compatibility while exposing tracked terminology.

PlaceholderAPI remains cache/profile based. Add aliases where useful:

- `%plexonquests_active_count%` -> existing active value;
- `%plexonquests_completed_count%` -> existing completed total;
- `%plexonquests_tracked%` -> existing pinned name;
- `%plexonquests_tracked_progress%` -> existing pinned progress;
- quest-specific state/progress placeholders only if they can be resolved without synchronous persistence access.

Offline/unloaded players return the existing neutral placeholder rather than forcing a database query.

## 12. Validation and reload

Candidate activation must validate:

- duplicate quest/pool IDs;
- missing prerequisite quests;
- self dependencies;
- cycles;
- disabled/unreachable prerequisite chains;
- invalid objectives and amounts;
- invalid rewards;
- invalid pool references/scope;
- invalid rarity references;
- invalid menu/MiniMessage content.

Reload remains:

`parse -> validate -> immutable candidate -> integration checks -> atomic swap`

Any activation-blocking 4.x graph failure keeps the previous registry active.

## 13. Regression matrix

Automated tests must cover the implementation contracts that can be validated off-server, including:

- prerequisite graph success/missing/self/cycle/unreachable cases;
- deterministic journal state derivation;
- tracked/pinned compatibility;
- destructive confirmation expiry/actor/target/stale/one-shot behavior;
- 3.3.1 persistence migration and restart reconstruction;
- claim reservation/idempotency regressions;
- exact ItemStack serialization regression;
- objective interest/index regressions;
- Core final-cancellation/provenance regressions already present;
- placeholder aliases and unloaded-player behavior;
- API immutability/additive compatibility;
- distribution contents/version/dependency isolation.

Do not claim an exact test total until exact-head CI reports it.

## 14. Publication readiness diagnostics

The RC must expose/check at least:

- plugin/version/build identity;
- loaded quest/pool/rarity counts and validation errors;
- dependency graph health;
- storage schema/open/queue/dirty state;
- uncertain claim count/status;
- integration status;
- Core Runtime acquisition/origin provider;
- Java/Paper compatibility in CI;
- final JAR contents and isolation;
- candidate SHA-256.

## 15. Documentation closure

Before RC freeze update:

- README;
- CHANGELOG;
- commands/permissions;
- configuration/quest authoring;
- API/PlaceholderAPI;
- integrations/Core provenance;
- migration 3.3.1 -> 4.0.0;
- backup/rollback;
- staging/runtime checklist.

## 16. RC closure

The branch stays unmerged.

For the exact candidate head:

1. compile with Java 25;
2. run the complete automated suite;
3. run distribution/JAR structure checks;
4. verify PlexonCore remains unshaded;
5. verify optional integrations remain unshaded;
6. verify SQLite runtime packaging;
7. produce the final RC JAR;
8. produce SHA256SUMS.txt;
9. open/update a draft Phase 2 PR;
10. freeze the exact candidate SHA;
11. if PlexonCraft runtime certification remains unavailable, publish `v4.0.0-rc.1` as a prerelease only.

Stable `v4.0.0` is forbidden until PlexonCraft runtime certification passes the migration, journal, objective/provenance, rewards, restart/reload, PlaceholderAPI, cross-plugin, Spark/MSPT and >=30-minute soak gates with zero HIGH/CRITICAL defects.
