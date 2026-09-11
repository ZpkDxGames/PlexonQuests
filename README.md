# PlexonQuests

[![Build](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml/badge.svg)](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml)

PlexonQuests 4.0 is the Phase 2 premium quest-journal release for Paper 26.2. It keeps the mature 3.3.1 objective, persistence, Core provenance, rotation, and reward-transaction engines while adding quest discovery, prerequisite-aware progression, derived journal states, tracked-quest terminology, a richer immutable API, safer destructive administration, and publication diagnostics.

`4.0.0-rc.1` is a release candidate. It must not be promoted to stable until the PlexonCraft runtime-certification checklist passes.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 2.0.4 recommended for shared Core Runtime acquisition/provenance; standalone compatibility remains supported
- Maven 3.9+ to build from source

There are no hard plugin dependencies. PlexonCore, PlaceholderAPI, Vault, LuckPerms, and supported Plexon plugins are runtime-detected. Required integrations fail closed rather than being approximated.

## Installation / upgrade

1. Back up `plugins/PlexonQuests/` before the first 4.0 startup.
2. Build with `mvn -B -ntp clean verify` or install the release-candidate JAR from the matching GitHub prerelease.
3. Copy `PlexonQuests-4.0.0-rc.1.jar` to `plugins/`.
4. Keep the existing 3.3.1 data directory; 4.0 preserves the SQLite assignments, frozen definition snapshots, claim identities, preferences, and history tables.
5. Start Paper and run `/quests validate` plus `/quests diagnostics`.
6. Exercise the runtime gates in `docs/RUNTIME_CERTIFICATION_4.0.0.md` before any stable promotion.

The installable artifact bundles SQLite JDBC and the relocated FastUtil runtime. PlexonCore, Paper, PlaceholderAPI, and optional integrations remain unshaded. `SHA256SUMS.txt` accompanies the candidate release.

## Premium journal

`/quests` now opens the Overview. The Phase 2 navigation model is:

- Overview
- Active Quests
- Available Quests
- Categories
- Tracked Quest
- Completed / History
- Statistics
- Help

The existing `/quests daily`, `/quests weekly`, `/quests milestones`, `/quests history`, claim/reroll/settings paths, and administrative commands remain compatible.

Player-facing journal states are derived rather than replacing the durable assignment state machine:

- `LOCKED`
- `AVAILABLE`
- `ACTIVE`
- `TRACKED`
- `COMPLETABLE`
- `COMPLETED`
- `COOLDOWN`
- `EXPIRED`
- `DISABLED`

The 3.x single `pinned` assignment remains the persistence/API compatibility contract; the 4.x product calls it the tracked quest.

## Quest prerequisites

Quest definitions may add a completion graph without changing existing definition snapshots:

```yaml
prerequisites:
  completed-quests:
    - first-steps
```

The complete graph is validated for invalid IDs, self-dependencies, missing references, disabled/unreachable dependencies, duplicate quest IDs, and cycles. Startup fails closed on an invalid graph. Player eligibility resolves prerequisite completion from a bounded asynchronous history cache; GUI rendering, PlaceholderAPI, and objective hot paths do not perform synchronous SQLite queries.

Existing assignment authority is preserved: daily/weekly quests come from deterministic rotations, milestones are lifecycle-assigned when eligible, and manual quests remain administrator-assigned. The discovery screen is read-only and does not create a second acceptance path.

## Objective and provenance model

The Phase 2 journal does not rewrite objective tracking. PlexonQuests keeps its indexed per-player objective-interest model and contribution-token deduplication. Block acquisition continues through the PlexonCore Runtime single-authority path when compatible Core is present, with conservative origin handling for natural-resource objectives. Player-placed and unknown origin state do not receive natural-only credit unless the existing contract explicitly permits it.

No SQLite/YAML lookup or global quest scan is added to contribution hot paths.

## Reward safety

The 3.3.1 claim architecture remains authoritative:

`preflight -> CLAIMING -> durable reservation -> reversible delivery -> irreversible boundary -> durable completion`

Exact serialized `ItemStack` payloads are restored for custom rewards. Duplicate live claims are rejected. Full-inventory handling follows the configured overflow policy. An uncertain transaction is never automatically redelivered.

Vault/Theosis remains the economy authority through the established Vault bridge where configured.

## Administration and reload safety

Destructive command mutations (`complete`, `reset`, and `cancel` forms with a concrete target) require a second identical command from the same actor within 30 seconds. Confirmation is one-shot, actor-bound, action/target-bound, and the original authoritative handler re-checks current target state before mutation.

Configuration reload remains asynchronous and candidate-based. The 4.x prerequisite graph has its own immutable known-good snapshot; invalid graph candidates are rejected instead of being activated for quest eligibility.

## PlaceholderAPI

Existing 3.x placeholders remain supported. Phase 2 adds aliases without synchronous persistence access:

- `%plexonquests_active_count%`
- `%plexonquests_completed_count%`
- `%plexonquests_tracked%`
- `%plexonquests_tracked_progress%`
- `%plexonquests_tracked_percentage%`
- `%plexonquests_tracked_time_left%`

The corresponding `active`, `completed_total`, and `pinned_*` identifiers remain compatible. Offline/unloaded players return the configured neutral placeholder rather than forcing a database query.

## Public API

The existing `PlexonQuestsAPI` remains registered. Phase 2 additionally registers `PlexonQuestsJournalAPI` through Bukkit's `ServicesManager`. Its views are immutable and expose journal state, scope/category, progress, tracked status, prerequisites, and missing prerequisites without exposing mutable persistence objects.

`QuestTrackEvent` is fired synchronously after the compatible pin/tracked assignment changes.

## Performance and persistence

- SQLite remains WAL-backed with one bounded writer and coalesced dirty progress.
- No task is created per quest, objective, player, or tracked quest.
- Assignment rows retain frozen definition snapshots.
- Completion prerequisite history is warmed asynchronously and bounded.
- GUI/inventory/Bukkit mutations remain on the primary thread.
- Async persistence and analysis operate on immutable/primitives-only data.
- Rotations wait for completion-history readiness before prerequisite-aware assignment.
- Core references/subscriptions are resolved outside contribution hot paths.

## Documentation

- [Phase 2 execution contract](docs/PHASE2_4.0.0_PREMIUM_REBUILD.md)
- [3.3.1 -> 4.0 migration and rollback](docs/MIGRATION_3.3.1_TO_4.0.0.md)
- [Phase 2 API, events, and PlaceholderAPI](docs/PHASE2_API_4.0.0.md)
- [PlexonCraft runtime certification](docs/RUNTIME_CERTIFICATION_4.0.0.md)
- [Commands and permissions](docs/COMMANDS.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Existing public API and events](docs/API.md)
- [Backup and recovery](docs/RECOVERY.md)
- [Performance model](docs/PERFORMANCE.md)
- [Integration contracts](docs/INTEGRATIONS.md)

## Building and verification

```bash
mvn -B -ntp clean verify
```

GitHub CI provisions the pinned PlexonCore 2.0.4 API, verifies Java 25, compiles and tests the exact head, checks the final JAR for `plugin.yml` and SQLite, requires the Core Runtime integration classes, rejects shaded PlexonCore and PlaceholderAPI runtime trees, runs `git diff --check`, and emits `SHA256SUMS.txt` with the build artifact.

Release-candidate publication is prerelease-only until runtime certification succeeds. Stable `v4.0.0` is intentionally blocked by the PlexonCraft migration, journal, objective/provenance, reward, persistence, integration, performance, and soak gates.

## Support and security

For reproducible defects include the Paper build, Java version, `/quests diagnostics`, relevant validation paths, and sanitized logs. With PlexonCore installed, also include `/plexon modules` and `/plexon diagnostics`. Report vulnerabilities according to [SECURITY.md](SECURITY.md).

PlexonQuests is available under the [MIT License](LICENSE).
