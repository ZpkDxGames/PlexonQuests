# PlexonQuests 3.3 — PlexonCore 2 Runtime and Shared Origin

PlexonQuests 3.3 moves high-frequency block-break acquisition and, when safe, block-origin authority onto PlexonCore 2 while keeping all quest gameplay semantics inside PlexonQuests.

## Supported platform

- Paper 26.2
- Java 25
- PlexonQuests 3.3.0
- PlexonCore 2.0.1 is the recommended Core runtime for authoritative shared-origin migration.

PlexonCore is a provided dependency. Its runtime classes are not shaded into the PlexonQuests JAR.

## Ownership boundary

PlexonCore owns shared block-break acquisition, immutable block facts, material routing, shared block-origin state, and Core runtime metrics.

PlexonQuests continues to own objective interest, filters, maturity checks, quest progress, assignments, rotations, rerolls, rewards, profiles, public API, GUI, and integration semantics.

The local `BlockBreakEvent` MONITOR listener remains as a small final-cancellation gate because the released Core 2 gateway captures its immutable block fact at HIGHEST. Cancelled events never create quest progress.

`BlockPlaceEvent` objective observation remains local in 3.3 because the released Core 2 API does not expose a block-place subscription.

## Runtime modes

Configure `core-runtime.mode` in `config.yml`:

### AUTO

Default. Uses Core Runtime when available. With PlexonCore 2.0.1 origin-import support, Core is the authoritative provenance provider. If the runtime or safe import contract is unavailable, PlexonQuests keeps the safe local provenance path.

### CORE

Requires a usable Core 2 Runtime. If enabled quest definitions require natural/player-placed filtering, safe Core origin-import support is also required. Startup fails rather than silently weakening provenance correctness.

### LOCAL

Keeps PlexonQuests local provenance and block acquisition behavior. This is the operational fallback mode and should be followed by a restart.

### SHADOW

Uses Core block-break acquisition but keeps PlexonQuests local provenance authoritative. Core and local origin results are compared and mismatch counters are exposed in diagnostics. Quest progress is still applied exactly once from the authoritative local result.

Changing authority mode at runtime is intentionally not hot-swapped. A restart is required.

## Legacy provenance migration

PlexonQuests 3.2 stored player-placed block provenance in chunk PDC. Version 3.3 retains that PDC for rollback and never deletes it during the Core migration.

Core 2.0.1 provides a persisted, idempotent chunk import contract. PlexonQuests imports legacy data lazily as relevant chunks are encountered:

- source ID: `plexonquests`
- source version: `1`
- imported facts: player-placed block coordinates
- completion marker owner: PlexonCore
- persistence: Core SQLite origin database

A Core completion marker is written in the same persistence transaction as the chunk import. Retrying the same source/version is safe.

Until Core confirms the persisted completion marker, PlexonQuests combines its frozen legacy snapshot with the Core event-time result. Existing player-placed blocks therefore cannot become countable as natural merely because a migration is in progress.

## UNKNOWN policy

`UNKNOWN` is never promoted to `NATURAL`.

For objectives that require a provenance decision, unknown or corrupt legacy data fails closed. This preserves the natural-block anti-exploit guarantee during startup, chunk loading, import failure, and recovery.

## Listener consolidation

In Core authoritative mode:

- Core owns provenance placement/break/burn/fade/explosion/piston/chunk maintenance;
- PlexonQuests does not register its local provenance listener;
- PlexonQuests does not mutate the legacy origin PDC;
- PlexonQuests retains only its small block MONITOR gate for final cancellation and progression.

In LOCAL or SHADOW mode, the local provenance listener remains active because local provenance is authoritative.

## Diagnostics

Run:

```text
/quests diagnostics
```

The 3.3 diagnostics append:

- runtime mode;
- origin provider (`CORE`, `LOCAL`, or `SHADOW`);
- runtime epoch;
- routed material count;
- whether Core origin was requested;
- Core events received and consumed;
- fallback and callback-failure counters;
- local final-cancellation gate state;
- local provenance listener state;
- SHADOW comparison and mismatch totals;
- origin-import tracked/pending chunks;
- marker checks and imports started;
- completed/failed imports;
- unknown chunks.

## Upgrade procedure

1. Back up the server and `plugins/PlexonQuests` data.
2. Install the verified PlexonCore 2.0.1 JAR.
3. Keep the existing PlexonQuests data/config directory.
4. Install the PlexonQuests 3.3 candidate.
5. Start in `SHADOW` for staging when validating an existing world.
6. Run `/quests diagnostics` and confirm Core Runtime is active.
7. Test a known natural block and a known player-placed block objective.
8. Review SHADOW mismatch/import counters.
9. After parity is established, set `core-runtime.mode: AUTO` or `CORE` and restart.
10. Run the production performance and coexistence checks before promoting a stable tag.

No quest reset is required.

## Rollback

Primary rollback target: PlexonQuests 3.2.0.

For an authority-only rollback, set:

```yaml
core-runtime:
  mode: LOCAL
```

and restart.

For a full rollback, stop the server and restore PlexonQuests 3.2.0. The legacy chunk PDC is retained by 3.3 specifically so the old local provenance model remains available. The Core import is additive and does not delete local provenance.

No destructive quest database migration is introduced by this runtime consolidation.

## Stable release gate

A successful Maven/MockBukkit/JAR build makes the source candidate-ready, but it is not by itself sufficient for a stable 3.3 release. Before a stable `v3.3.0` promotion, record real Paper evidence for standalone behavior, Core Runtime behavior, shared-origin correctness, natural-block anti-exploit behavior, duplicate-progress prevention, PlexonTools 4.3 coexistence, restart behavior, soak behavior, and Spark/performance neutrality or improvement.

If those runtime measurements have not been performed, publish and test a release candidate instead of labeling the build stable.
