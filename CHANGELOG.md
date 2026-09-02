# Changelog

All notable changes to PlexonQuests are documented here.

## [Unreleased]

## [2.0.0] - 2026-09-02

- Reorganized the journal around four scope tabs, a compact quest grid, and one consistent control row.
- Reduced card lore and moved complete objective and reward information into the details view.
- Fixed component placeholders leaking into lore and hid vanilla item attack attributes in GUI tooltips.
- Fixed incoherent progress totals for `ANY` quests and a phantom final page in quest history.
- Hid invalid pin, reroll, and claim actions based on assignment state and disabled actions in admin previews.
- Serialized reroll preparation on the primary thread to prevent duplicate reservations and unsafe Bukkit access.
- Added automatic, backed-up migration from menu layout 1 to layout 2 and recursive GUI slot validation.
- Made build and release JAR discovery version-independent.

## [1.0.0] - 2026-09-01

- Initial standalone Paper 26.2 quest engine.
- Daily, weekly, milestone, and manual assignments.
- SQLite/WAL persistence with cached, coalesced progress writes.
- Secure player journal and administrator diagnostics menus.
- Configurable MiniMessage presentation, effects, rotations, and rewards.
- Optional Plexon, PlaceholderAPI, Vault, and LuckPerms integration discovery.
