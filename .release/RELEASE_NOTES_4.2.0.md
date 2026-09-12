# PlexonQuests 4.2.0

PlexonQuests 4.2.0 changes normal quest participation from automatic assignment to an explicit join-based Quest Board while preserving the 4.1 persistence, reward, objective-index, provenance and transaction architecture.

## Player experience

- `/quests` now centers Current, Available, Completed/History, Skills and Help.
- Players explicitly choose a Daily, Weekly or eligible Milestone quest.
- One normal active quest is allowed by default; completed-but-unclaimed quests do not occupy that runtime slot.
- Daily/Weekly slot settings remain period participation budgets.
- Abandon requires confirmation and consumes period budget by default.
- Existing 4.1 multi-active profiles remain non-destructive legacy overflow and cannot join another normal quest until within policy.
- The summary uses the player's actual head.
- PlexonSkills is optional and shown as read-only progression context when available; unavailable/loading skills never disable quests.
- Reroll remains a compatibility/deprecation route back to Available Quests.

## Runtime and safety

- Normal Daily/Weekly/Milestone quests are no longer auto-created by lifecycle maintenance.
- Catalog offers are side-effect free and deterministic per player/period/server seed while retaining weighted pools, mix constraints and recent-history exclusion.
- `QuestParticipationService` is the player join/abandon authority and rechecks active/period limits, duplicate history, eligibility and stale periods before insertion.
- Existing objective-interest indexing remains authoritative: no joined quest means no normal objective candidates; one joined quest means only that active quest's objectives are indexed.
- Persistence remains asynchronous; Bukkit/player mutations remain primary-thread authoritative.
- No per-player, per-quest or GUI redraw scheduler was added.
- Menu layout 4 honors configured filler and safely preserves customized menu files during migration.

## Compatibility

Java 25, Paper `26.2.build.121-stable`, PlexonCore `2.0.4`, existing SQLite assignment/history data and 4.1 frozen snapshots remain the release boundary. Public/admin assignment, claim, tracking and diagnostics remain available.

GitHub CI/release verification is source/distribution evidence. Live PlexonCraft/Spark runtime certification is not claimed unless separately executed.

Rollback: `v4.1.0` at `3b0082bc181b3765e7cdb2bf4e9ce6c72206b896`.
