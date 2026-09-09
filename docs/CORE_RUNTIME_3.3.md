# PlexonQuests 3.3 — PlexonCore 2 Runtime and Shared Origin

PlexonQuests 3.3 moves high-frequency block-break acquisition and, when safe, block-origin authority onto PlexonCore 2 while keeping all quest gameplay semantics inside PlexonQuests.

## Supported platform

- Paper 26.2
- Java 25
- PlexonQuests 3.3.0
- PlexonCore 2.0.2 is the recommended and verified Core runtime.

PlexonCore is a provided dependency. Its runtime classes are not shaded into the PlexonQuests JAR.

## Ownership boundary

PlexonCore owns shared block-break acquisition, immutable block facts, material routing, shared block-origin state, final cancellation outcome, and Core runtime metrics.

PlexonQuests continues to own objective interest, filters, maturity checks, quest progress, assignments, rotations, rerolls, rewards, profiles, public API, GUI, and integration semantics.

PlexonCore 2.0.2 dispatches block subscribers at final `MONITOR`, suppresses finally cancelled breaks, and captures event-time origin before provenance cleanup. PlexonQuests retains an additional local cancellation check as a defensive quest-side invariant.

`BlockPlaceEvent` objective observation remains local in 3.3 because the released Core 2 API does not expose a block-place subscription.

## Runtime modes

Configure `core-runtime.mode` in `config.yml`:

### AUTO
Default. Uses Core Runtime when available. With PlexonCore 2.0.2 origin-import support, Core is the authoritative provenance provider. If the runtime or safe import contract is unavailable, PlexonQuests keeps the safe local provenance path.

### CORE
Requires a usable Core 2 Runtime. If enabled quest definitions require natural/player-placed filtering, safe Core origin-import support is also required. Startup fails rather than silently weakening provenance correctness.

### LOCAL
Keeps PlexonQuests local provenance and block acquisition behavior. This is the operational fallback mode and should be followed by a restart.

### SHADOW
Uses Core block-break acquisition but keeps PlexonQuests local provenance authoritative. Core and local origin results are compared and mismatch counters are exposed in diagnostics. Quest progress is still applied exactly once from the authoritative local result.

Changing authority mode at runtime is intentionally not hot-swapped. A restart is required.

## Legacy provenance migration

PlexonQuests 3.2 stored player-placed block provenance in chunk PDC. Version 3.3 retains that PDC for rollback and never deletes it during the Core migration.

Core 2.0.2 preserves the persisted, idempotent chunk import contract introduced in 2.0.1. PlexonQuests imports legacy data lazily as relevant chunks are encountered:

- source ID: `plexonquests`
- source version: `1`
- imported facts: player-placed block coordinates
- completion marker owner: PlexonCore
- persistence: Core SQLite origin database

A Core completion marker is written in the same persistence transaction as the chunk import. Retrying the same source/version is safe.

Until Core confirms the persisted completion marker, PlexonQuests combines its frozen legacy snapshot with the Core event-time result. Existing player-placed blocks therefore cannot become countable as natural merely because a migration is in progress.

## UNKNOWN policy

`UNKNOWN` is never promoted to `NATURAL`. For objectives that require a provenance decision, unknown or corrupt legacy data fails closed.

## Listener consolidation

In Core authoritative mode, Core owns provenance lifecycle and final block outcomes; PlexonQuests does not register its local provenance listener or mutate legacy origin PDC. PlexonQuests retains its small block listener to match the Core fact to the Bukkit event and enforce quest-side invariants.

In LOCAL or SHADOW mode, the local provenance listener remains active because local provenance is authoritative.

## Diagnostics

Run `/quests diagnostics` to inspect runtime mode, origin provider, runtime epoch, routed materials, Core events received/consumed, fallbacks, callback failures, local listener state, SHADOW comparisons/mismatches, and origin-import counters.

## Upgrade procedure

1. Back up the server and `plugins/PlexonQuests` data.
2. Install verified PlexonCore 2.0.2.
3. Keep the existing PlexonQuests data/config directory.
4. Install PlexonQuests 3.3.0.
5. Start in `SHADOW` for staging when validating an existing world.
6. Run `/quests diagnostics` and confirm `CORE_RUNTIME`.
7. Test known natural and player-placed block objectives, including a cancelled break.
8. Review SHADOW mismatch/import counters.
9. After parity is established, set `core-runtime.mode: AUTO` or `CORE` and restart.

No quest reset is required.

## Rollback

Set `core-runtime.mode: LOCAL` and restart for an authority-only rollback, or restore PlexonQuests 3.2.0 for a full rollback. Legacy chunk PDC is retained and Core import is additive.

## Release verification

The 3.3 build/release workflows download the exact PlexonCore 2.0.2 release JAR and verify SHA-256 `7e096ada4293017203cd4e01cb93761d169fa4c756fc46af520b2aed11a10ef5` before compiling. The resulting PlexonQuests JAR is checked for required runtime classes and rejected if PlexonCore classes were shaded into it.
