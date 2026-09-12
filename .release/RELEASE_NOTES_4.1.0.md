# PlexonQuests 4.1.0

PlexonQuests 4.1.0 is the final stable full-revamp release for Paper 26.2 and Java 25.

## Player experience

- Premium Quest Journal GUI with hardened click routing and next-tick exact-holder transitions.
- Correct MiniMessage presentation across quest-facing text and readable low-progress rendering.
- Clear active, eligible, tracked, claimable, completed and locked states without changing quest economy semantics.

## High-concurrency runtime

- Common gameplay listeners now perform a constant-time server-wide objective-type rejection before detailed entity, recipe, inventory or contribution work.
- Existing per-player material/entity candidate indexes remain the authoritative second-stage filter.
- Travel/play-time sampling skips square-root work for stationary and rejected outlier deltas.
- GUI click debounce uses a main-thread HashMap rather than a concurrent map and relies on the centralized click router for semantic action validation.
- Progress remains in-memory on the primary thread while persistence stays on the bounded single-writer SQLite/WAL path.

## Compatibility

- No storage/schema migration.
- No quest/reward economy rebalance.
- No public API or command removal.
- Paper API: 26.2.
- PlexonCore: 2.0.4.
- Rollback: `v4.0.0` (`57a235460e5e936ca931ee8d0e96b0a424f134f8`).

The stable release workflow rebuilds and tests the exact `main` SHA, verifies the installable JAR and dependency boundaries, publishes SHA-256/test/provenance evidence, then downloads and verifies the published assets again. Live MSPT remains host-dependent and must be measured on the target server rather than inferred from unit tests.
