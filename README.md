# PlexonQuests

[![Build](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml/badge.svg)](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml)

PlexonQuests is a GUI-first quest and challenge engine for Paper 26.2. It ships with daily, weekly, milestone, and manual quest support; deterministic rotations; guarded reward claims; SQLite persistence; configurable MiniMessage presentation; and an immutable public API.

## Requirements

- Paper 26.2
- Java 25
- Maven 3.9 or newer to build from source

There are no hard plugin dependencies. PlaceholderAPI, Vault, LuckPerms, and supported Plexon plugins are detected at runtime. A quest or pool that requires an unavailable integration is excluded instead of failing the plugin or silently approximating an event.

## Installation

1. Build with `mvn -B -ntp clean verify`, or download the JAR from the [latest GitHub release](https://github.com/ZpkDxGames/PlexonQuests/releases/latest).
2. Copy `PlexonQuests-2.0.1.jar` into the server's `plugins` directory.
3. Start Paper once to create `plugins/PlexonQuests/` and the default definitions.
4. Run `/quests validate` and `/quests diagnostics` as an operator.
5. Review the reset timezone, worlds, rewards, and optional integration requirements before opening the server to players.

Do not copy an unshaded Maven JAR. The installable artifact contains the SQLite JDBC driver and is checked by CI before upload. `SHA256SUMS.txt` accompanies the workflow artifact.

## Player experience

- `/quests` opens the journal.
- Daily and weekly assignments remain stable through reloads and restarts.
- Milestones are assigned once their eligibility conditions are met.
- Progress can use `ALL`, `ANY`, or ordered `SEQUENCE` completion.
- Completed rewards use a reserve/deliver/commit claim transaction to resist double clicks.
- Players can pin quests, inspect history, reroll eligible assignments, and control feedback channels.
- The 2.0 journal uses four clear scope tabs and compact cards; full objectives and rewards stay in the details view.

The clean install includes 15 daily quests, 12 weekly quests, 6 milestones, two weighted pools, five rarities, menu layouts, effects, and messages. The core catalog can fill every configured rank slot even when optional Plexon integrations are absent.

## Operational design

- SQLite runs in WAL mode through one bounded writer queue; gameplay listeners do not write to the database directly.
- Assignment rows retain frozen definition snapshots, so edits do not reinterpret already-issued quests.
- Natural-block tracking uses chunk-local position sets and persistent chunk data. Unknown/corrupt origin state fails closed for `NATURAL_ONLY` objectives.
- Configuration reload builds and validates a complete candidate snapshot before atomically replacing the active snapshot.
- External progress source tokens are stored as SHA-256 hashes and deduplicated across restarts.
- A server stop after reward delivery was reserved converts the transaction to `UNCERTAIN`; it is never automatically delivered again.

## Optional integrations

| Integration | Capability when a supported public API is present |
| --- | --- |
| PlaceholderAPI | `%plexonquests_*%` placeholders |
| Vault | Paid rerolls and money rewards |
| LuckPerms | Permission rewards |
| PlexonRanks | Rank-category slot bonuses and rank-up progress |
| Plexon DailyRewards | Successful daily-reward claim progress |
| PlexonTools, PlexonKeys, PlexonCrates, PlexonShops | Compatibility status and eligibility gating; progress must use a verified public event/API or `submitProgress` |

Unsupported or missing API surfaces are reported as unavailable. PlexonQuests does not scrape lore, chat, commands, or internal plugin state.

## Documentation

- [Commands and permissions](docs/COMMANDS.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Public API and events](docs/API.md)
- [Backup, migration, and recovery](docs/RECOVERY.md)
- [Performance model and profiling](docs/PERFORMANCE.md)
- [Live staging checklist](docs/STAGING.md)

## Building and testing

```bash
mvn -B -ntp clean verify
```

The build compiles for Java 25, runs unit and SQLite restart tests, starts the plugin under MockBukkit, produces JaCoCo output, shades runtime libraries, checks `plugin.yml` and SQLite in the final JAR, and generates a SHA-256 checksum. Release publication is tag-driven by `.github/workflows/release.yml`; ordinary `main` builds do not create a GitHub release.

## Support and security

Use GitHub issues for reproducible defects and include the Paper build, Java version, `/quests diagnostics`, relevant validation paths, and sanitized logs. Report vulnerabilities according to [SECURITY.md](SECURITY.md).

PlexonQuests is available under the [MIT License](LICENSE).
