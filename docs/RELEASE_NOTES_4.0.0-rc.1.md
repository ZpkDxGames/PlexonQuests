# PlexonQuests 4.0.0-rc.1

Phase 2 premium quest-journal release candidate. Runtime certification is still required before stable promotion.

## Highlights

- New `/quests` Overview plus Active, Available, Categories, Tracked, Completed/History, Statistics, and Help journal surfaces.
- Explicit derived player-facing states without replacing the durable 3.3.1 assignment state machine.
- Optional completion prerequisites through `prerequisites.completed-quests`.
- Full prerequisite graph validation for missing/self/cyclic/disabled-unreachable dependencies.
- Bounded asynchronous completion-history cache so eligibility, GUI, and placeholders avoid synchronous SQLite history reads.
- Prerequisite-aware rotation/milestone eligibility while preserving the existing single assignment authority.
- Tracked quest terminology layered over the compatible 3.x pinned-assignment contract.
- `QuestTrackEvent` plus an additive immutable `PlexonQuestsJournalAPI` Bukkit service.
- Placeholder aliases for active/completed counts and tracked quest information.
- Staged, expiring, actor/action/target-bound confirmation for destructive reset/complete/cancel commands before the original authoritative mutation path executes.
- Candidate-version-aware distribution checks with Java 25, SQLite packaging, PlexonCore isolation, PlaceholderAPI isolation, and SHA-256 generation.

## Preserved mature systems

This release intentionally does not rewrite:

- indexed objective interest and contribution deduplication;
- PlexonCore 2 Runtime block acquisition/provenance authority;
- SQLite WAL/single-writer/coalesced persistence;
- deterministic daily/weekly rotations;
- exact serialized custom ItemStack rewards;
- claim reservation, reward preflight, rollback, irreversible boundary, and uncertain transaction handling;
- 3.x `PlexonQuestsAPI` methods and existing pinned placeholders/commands.

## Upgrade notes

Back up `plugins/PlexonQuests/` before staging the RC. Existing 3.3.1 persistent state is retained; Phase 2 journal states are derived rather than stored over assignment states. See `MIGRATION_3.3.1_TO_4.0.0.md`.

Existing quest definitions do not need prerequisites. If prerequisites are added, referenced quest IDs must exist and the graph must remain acyclic/reachable.

## Release status

`4.0.0-rc.1` is a GitHub prerelease candidate only. Do not merge the Phase 2 PR or publish stable `v4.0.0` until the PlexonCraft runtime checklist, Spark/MSPT comparison, and >=30-minute soak all pass with zero HIGH/CRITICAL defects.
