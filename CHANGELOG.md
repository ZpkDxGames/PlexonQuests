# Changelog

All notable changes to PlexonQuests are documented here.

## [Unreleased]

## [2.0.1] - 2026-09-05

- Expanded the bundled catalog to 15 daily quests, 12 weekly quests, and 6 retroactive milestones, with enough dependency-free variety to fill every configured slot.
- Reorganized journal tabs and details around availability, used slots, claimable totals, exact reset times, objective filters, completion rules, and reward sections.
- Added backed-up migrations for the default menu layout 3 and exact, untouched legacy pools to catalog version 2; customized pools remain unchanged.
- Fixed claimed rotating quests incorrectly freeing a slot before their period ended, including after a reconnect.
- Fixed selection and reroll constraints not accounting for existing assignments, and prevented same-period repeats that conflict with durable uniqueness.
- Fixed reroll races with progress, claims, expiration, disconnects, and competing confirmations.
- Backfilled new `QUESTS_CLAIMED` milestone progress from each player's persisted claimed total.
- Made rank-based slot changes refresh for online players and added capacity and invalid-category validation warnings.
- Applied the root recent-history fallback when a pool omits its own window, enforced the global contribution cooldown, and made origin mode `OFF` truly ignore origin filters.
- Rejected reward claims after the configured completion grace deadline and enforced the manual assignment limit through the public API.

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
