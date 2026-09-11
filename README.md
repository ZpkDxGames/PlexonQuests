# PlexonQuests

[![Build](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml/badge.svg)](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml)

PlexonQuests **4.0.0** is the stable premium quest-journal line for Paper 26.2. It preserves the mature objective, persistence, PlexonCore provenance, rotation and reward-transaction engines while adding prerequisite-aware discovery, derived journal states, tracked-quest terminology, richer immutable API views and the final Phase 3 premium journal UX.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 2.0.4 recommended for shared Core Runtime acquisition/provenance; safe standalone compatibility remains available where supported
- Maven 3.9+ when building from source

PlexonCore, PlaceholderAPI, Vault, LuckPerms and supported Plexon plugins are runtime-detected integrations. SQLite JDBC and relocated FastUtil are bundled in the plugin JAR; optional/provider API trees remain unshaded.

## Installation / upgrade

1. Back up `plugins/PlexonQuests/` before the first 4.0 startup.
2. Install `PlexonQuests-4.0.0.jar` from the stable GitHub release.
3. Keep the existing 3.3.1 data directory; 4.0 preserves SQLite assignments, frozen definition snapshots, claim identities, preferences and history tables.
4. Start Paper and run `/quests validate` plus `/quests diagnostics`.
5. Perform the operational checks in `docs/RUNTIME_CERTIFICATION_4.0.0.md` before production cutover/soak sign-off where required.

GitHub stable publication is source/release certification. It does not imply that a live PlexonCraft deployment or soak has been executed.

## Premium journal

`/quests` opens the Quest Journal. The player-facing navigation includes:

- Journal Home
- Active Quests
- Eligible Quests
- Completed Quests
- Tracked Quest
- Quest Details
- Help

Categories, scope and discovery state are contextual filters rather than competing persistence models. Existing daily, weekly, milestone, history, claim, reroll, settings and administrative command paths remain compatible.

The final RC3 54-slot control geometry is:

- slot 4 — section identity
- slot 45 — Back / Journal Home
- slot 48 — Previous
- slot 49 — page/context
- slot 50 — Next
- slot 53 — primary contextual action

Journal actions use bounded rapid-click debounce. Track/Untrack presentation updates in place instead of reopening the menu solely for refresh. Completed-history rendering remains asynchronous and stale-safe.

## Journal states and prerequisites

Player-facing journal state is derived from authoritative quest/profile state rather than introducing a second state machine. Definitions may declare completion prerequisites such as:

```yaml
prerequisites:
  completed-quests:
    - first-steps
```

The graph is validated for invalid IDs, self-dependencies, missing references, disabled/unreachable dependencies, duplicate IDs and cycles. Invalid candidate configuration fails closed. Completion history is warmed asynchronously and bounded; journal rendering, PlaceholderAPI and objective hot paths do not synchronously query SQLite.

Discovery is read-only. Daily/weekly quests remain rotation-assigned, milestones remain lifecycle-assigned when eligible, and manual quests remain administrator-assigned.

## Objective and provenance model

High-frequency progress uses per-player objective indexes with cheap type/material/entity interest gates. Expensive origin, maturity or spawn-reason context is requested only when a matching objective needs it.

Ordinary main-thread contributions use direct in-memory progression. Contributions carrying durable source tokens reserve those tokens through persistence before application to prevent duplicate external progress.

Block acquisition continues through the PlexonCore Runtime single-authority path when compatible Core is present. Natural-resource objectives remain fail-closed for unknown/player-placed origin where the definition requires natural acquisition. No SQLite/YAML lookup or global quest scan is introduced into contribution hot paths.

## Reward safety

The claim architecture remains:

`preflight -> CLAIMING -> durable reservation -> ordered delivery -> durable completion`

Duplicate live claims are rejected. Full-inventory handling follows the configured overflow policy. Exact serialized `ItemStack` payloads are restored within configured size limits.

Irreversible console-command and Plexon-key fallback rewards are ordered after reversible reward types. If side effects have already occurred and final delivery/commit becomes ambiguous, the transaction is recorded as **uncertain** rather than silently rolled back and redelivered.

Vault/Theosis remains the economy authority through the Vault bridge when configured.

## Persistence, rotation and reload

- SQLite remains WAL-backed with one bounded writer and coalesced dirty progress.
- No task is created per quest, objective, player or tracked quest.
- Assignment rows retain frozen definition snapshots.
- Completion/prerequisite history is asynchronous and bounded.
- Rotations wait for completion-history readiness before prerequisite-aware assignment.
- Configuration reload remains candidate-based; invalid candidates do not partially replace the active known-good snapshot.
- GUI/inventory/Bukkit mutations stay on the primary thread.

## PlaceholderAPI and public API

Existing `%plexonquests_*%` placeholders remain supported, including active/completed/tracked aliases. Unloaded/offline players return the configured neutral result rather than forcing a persistence read.

The existing `PlexonQuestsAPI` remains registered. `PlexonQuestsJournalAPI` exposes immutable journal views for state, scope/category, progress, tracking and prerequisites without exposing mutable persistence objects. `QuestTrackEvent` fires after successful tracked/pinned state changes.

## Administration

Destructive targeted administration such as complete/reset/cancel uses a second identical confirmation from the same actor within the configured confirmation window. Confirmation is one-shot and actor/action/target-bound, and the authoritative handler re-checks current state before mutation.

`/quests diagnostics` reports runtime/provenance, persistence, integration and routing state. `/quests validate` validates the current configuration/definition graph.

## Build and release verification

```bash
mvn -B -ntp clean verify
```

CI verifies:

- final RC3, accepted Phase 3 and performance ancestry;
- Java 25 / class major 69;
- Paper 26.2 and PlexonCore 2.0.4;
- a non-empty test suite with zero failures, errors or skips;
- required plugin resources and Core Runtime integration classes;
- SQLite JDBC inclusion;
- relocated FastUtil inclusion with no unrelocated FastUtil tree;
- no shaded PlexonCore, PlaceholderAPI, Bukkit/Paper, Adventure, LuckPerms or Vault API trees;
- SHA-256, source provenance and whitespace.

The stable publisher only accepts a non-prerelease project version from exact current `main`. It rebuilds/tests that source, publishes the JAR plus checksum/test/provenance evidence, downloads the published assets and verifies their checksum and exact source commit before completing.

Live PlexonCraft runtime certification and soak remain deployment follow-ups and may be recorded as `NOT_EXECUTED` in GitHub release provenance. Rollback baseline: `v3.3.1` at `b74fc212d5aea41a0e01e9bc1bf5c5382302d014`.

## Documentation

- [3.3.1 -> 4.0 migration and rollback](docs/MIGRATION_3.3.1_TO_4.0.0.md)
- [Phase 3 player UX](docs/PHASE3_PLAYER_UX.md)
- [PlexonCraft runtime certification](docs/RUNTIME_CERTIFICATION_4.0.0.md)
- [Commands and permissions](docs/COMMANDS.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Public API and events](docs/API.md)
- [Backup and recovery](docs/RECOVERY.md)
- [Performance model](docs/PERFORMANCE.md)
- [Integration contracts](docs/INTEGRATIONS.md)

## Support and security

For reproducible defects include the Paper build, Java version, `/quests diagnostics`, relevant validation paths and sanitized logs. With PlexonCore installed, also include `/plexon modules` and `/plexon diagnostics`. Report vulnerabilities according to [SECURITY.md](SECURITY.md).

PlexonQuests is available under the [MIT License](LICENSE).
