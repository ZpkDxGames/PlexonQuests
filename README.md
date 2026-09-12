# PlexonQuests

[![Build](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml/badge.svg)](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml)

PlexonQuests **4.2.0** is a join-based quest board for Paper 26.2 / Java 25. It preserves the mature 4.1 objective, persistence, reward-transaction, PlexonCore provenance and integration architecture while changing normal participation to explicit player choice.

> PlexonQuests 4.2 no longer automatically starts normal quests. Players choose a quest from `/quests`, and only one normal quest runs at a time by default.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 2.0.4 recommended for shared Core Runtime acquisition/provenance
- Maven 3.9+ for source builds

SQLite JDBC and relocated FastUtil are bundled. PlaceholderAPI, Vault, LuckPerms and Plexon-family integrations including PlexonSkills are optional/runtime-detected.

## Quest Board

`/quests` exposes:

- **Current** — running quest, progress, objectives, rewards and guarded abandon/claim actions.
- **Available** — deterministic Daily, Weekly and eligible Milestone offers.
- **Completed / History** — asynchronous paginated persisted history.
- **Skills** — optional PlexonSkills level/progress context.
- **Help** — participation, budget and compatibility rules.

The summary uses the player's real Minecraft head. Skills are informational in 4.2 and are not silently introduced as quest eligibility requirements.

## Participation model

- Normal Daily/Weekly/Milestone quests are not auto-assigned.
- `participation.maximum-active-quests` defaults to `1`.
- Completing a quest frees the active runtime slot even before reward claim.
- Daily/Weekly slot settings remain the source of period participation budgets rather than simultaneous normal assignments.
- The same rotating quest cannot be joined twice in one period.
- Abandon is confirmation-gated and consumes period budget by default.
- Existing 4.1 multi-active assignments are preserved; overflow players finish those assignments before joining another normal quest.
- Manual/admin assignments and existing public assignment APIs remain supported separately from normal player participation.

## Runtime model

PlexonQuests continues to use shared interest-gated listeners, not one listener per player/quest.

`no joined quest -> no normal objective candidates`

`one joined quest -> only that active quest's normal objective candidates`

Completion, cancellation and expiration reindex the player profile and remove inactive objective interest. Travel/play-time sampling remains interest-gated. SQLite/catalog/history work stays asynchronous; Bukkit/inventory mutations stay on the primary thread. No repeating GUI redraw task is used.

## Configuration and migration

Config schema remains version 1 and adds `participation`, `catalog` and `skills-display`. `menus.yml` advances to layout 4 and the player journal uses `common.filler.material`. Exact bundled v3 defaults are backed up/replaced; customized older menu files are backed up and preserved rather than blindly overwritten.

See [4.1 -> 4.2 migration](docs/MIGRATION_4.1.0_TO_4.2.0.md), [configuration](docs/CONFIGURATION.md), [commands](docs/COMMANDS.md) and [player UX](docs/PLAYER_UX_4.2.md).

## Build/release verification

```bash
mvn -B -ntp clean verify
```

Canonical CI verifies ancestry, Java class major 69, exact Paper/PlexonCore boundaries, a non-empty all-green test suite, installable JAR structure, bundled SQLite, relocated FastUtil, provided-dependency non-shading, SHA-256, test summary, provenance and whitespace.

Stable publication accepts exact current `main`, rebuilds/tests it, publishes `PlexonQuests-4.2.0.jar` plus `SHA256SUMS.txt`, `TEST_SUMMARY.txt` and `PROVENANCE.txt`, then re-downloads/verifies public assets. This does not imply live PlexonCraft/Spark certification.

Rollback baseline: `v4.1.0` (`3b0082bc181b3765e7cdb2bf4e9ce6c72206b896`).

PlexonQuests is available under the [MIT License](LICENSE).
