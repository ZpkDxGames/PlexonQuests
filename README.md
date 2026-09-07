# PlexonQuests

[![Build](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml/badge.svg)](https://github.com/ZpkDxGames/PlexonQuests/actions/workflows/build.yml)

PlexonQuests is a GUI-first quest and challenge engine for Paper 26.2. It ships with daily, weekly, milestone, and manual quest support; deterministic rotations; guarded reward claims; SQLite persistence; configurable MiniMessage presentation; and an immutable public API.

PlexonQuests 3.1.0 is Core-aware. With compatible PlexonCore 1.x installed it registers as Core module `quests`; without Core it keeps the standalone compatibility behavior of the 3.0.0 production baseline.

## Requirements

- Paper 26.2
- Java 25
- PlexonCore 1.0.0 / Core API 1.x recommended for Core mode; optional at runtime in 3.1.0
- Maven 3.9 or newer to build from source

There are no hard plugin dependencies. PlexonCore, PlaceholderAPI, Vault, LuckPerms, and supported Plexon plugins are detected at runtime. A quest or pool that requires an unavailable integration is excluded instead of failing the plugin or silently approximating an event.

## Installation

1. Build with `mvn -B -ntp clean verify`, or download the JAR from the [latest GitHub release](https://github.com/ZpkDxGames/PlexonQuests/releases/latest).
2. Copy `PlexonQuests-3.1.0.jar` into the server's `plugins` directory.
3. For Core mode, keep `PlexonCore-1.0.0.jar` installed. PlexonQuests remains able to start in standalone mode when Core is absent.
4. Keep the existing `plugins/PlexonQuests/` folder when upgrading from 3.0.0; 3.1.0 does not move or reset quest data.
5. Start Paper and run `/quests validate` and `/quests diagnostics`. In Core mode also run `/plexon modules` and `/plexon diagnostics`.
6. Review the reset timezone, worlds, rewards, and optional integration requirements before opening the server to players.

Do not copy an unshaded Maven JAR. The installable artifact contains the SQLite JDBC driver and is checked by CI before upload. PlexonCore is a `provided` compile dependency and its runtime classes must not be bundled inside the Quests JAR. `SHA256SUMS.txt` accompanies the release artifact.

## Player experience

- `/quests` opens the journal.
- Daily and weekly assignments remain stable through reloads and restarts.
- Milestones are assigned once their eligibility conditions are met.
- Progress can use `ALL`, `ANY`, or ordered `SEQUENCE` completion.
- Completed rewards use a reserve/deliver/commit claim transaction to resist double clicks.
- Players can pin quests, inspect history, reroll eligible assignments, and control feedback channels.
- The journal uses four clear scope tabs and compact cards; full objectives and rewards stay in the details view.

The clean install includes 15 daily quests, 12 weekly quests, 6 milestones, two weighted pools, five rarities, menu layouts, effects, and messages. The core catalog can fill every configured rank slot even when optional Plexon integrations are absent.

Core adoption in 3.1.0 is intentionally infrastructure-only from the player's perspective: quest definitions, rotations, progress, claims, rewards, storage, GUI behavior, PAPI ownership, and player data remain owned by PlexonQuests.

## Operational design

- SQLite runs in WAL mode through one bounded writer queue; gameplay listeners do not write to the database directly.
- Assignment rows retain frozen definition snapshots, so edits do not reinterpret already-issued quests.
- Natural-block tracking uses chunk-local position sets and persistent chunk data. Unknown/corrupt origin state fails closed for `NATURAL_ONLY` objectives.
- Configuration reload builds and validates a complete candidate snapshot before atomically replacing the active snapshot.
- External progress source tokens are stored as SHA-256 hashes and deduplicated across restarts.
- A server stop after reward delivery was reserved converts the transaction to `UNCERTAIN`; it is never automatically delivered again.
- Core references are resolved at lifecycle initialization; gameplay events do not perform per-event Core service lookups or registry scans.

## Optional integrations

| Integration | Capability when a supported public API is present |
| --- | --- |
| PlexonCore 1.x | Module registration, ecosystem visibility, broad provider discovery hints, and shared Core diagnostics |
| PlaceholderAPI | `%plexonquests_*%` placeholders |
| Vault | Paid rerolls and money rewards |
| LuckPerms | Permission rewards |
| PlexonRanks | Rank-category slot bonuses, rank metadata filters, immediate slot refresh, durable rank-up progress |
| PlexonCrates | Crate-open progress from the public `CrateOpenEvent`, including transaction-safe deduplication and crate/key/reward metadata |
| Plexon DailyRewards | Successful claim progress with tier/day metadata and duplicate-resistant daily source tokens |
| PlexonTools, PlexonKeys, PlexonShops | Adapter-ready; activates only when the installed provider exposes the supported public event contract. Current provider builds without that contract remain fail-closed and are reported by diagnostics. |

Core provider state is only a broad discovery hint. PlexonQuests still validates the exact event/API contract required for quest progress and does not scrape lore, chat, commands, or internal plugin state.

## Documentation

- [PlexonCore integration and 3.1.0 migration](docs/PLEXONCORE.md)
- [Commands and permissions](docs/COMMANDS.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Public API and events](docs/API.md)
- [Backup, migration, and recovery](docs/RECOVERY.md)
- [Performance model and profiling](docs/PERFORMANCE.md)
- [Live staging checklist](docs/STAGING.md)
- [Integration contracts and metadata](docs/INTEGRATIONS.md)

## Building and testing

```bash
mvn -B -ntp clean verify
```

PlexonCore is a `provided` dependency. GitHub CI and the tag-driven release workflow provision the exact released `PlexonCore-1.0.0.jar`, verify its pinned SHA-256, and install it only into the workflow-local Maven repository before the build. The final distribution check rejects any Quests JAR containing a second `com/zpkdxgames/plexoncore/` runtime tree.

The build compiles for Java 25, runs unit and SQLite restart tests, starts the plugin under MockBukkit, exercises Core module lifecycle and critical provider-event regressions, produces JaCoCo output, shades Quests runtime libraries, checks `plugin.yml` and SQLite in the final JAR, and generates a SHA-256 checksum. Release publication is tag-driven by `.github/workflows/release.yml`; ordinary `main` builds do not create a GitHub release.

## Support and security

Use GitHub issues for reproducible defects and include the Paper build, Java version, `/quests diagnostics`, relevant validation paths, and sanitized logs. In Core mode also include `/plexon modules` and `/plexon diagnostics`. Report vulnerabilities according to [SECURITY.md](SECURITY.md).

PlexonQuests is available under the [MIT License](LICENSE).
