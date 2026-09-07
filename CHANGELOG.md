# Changelog

All notable changes to PlexonQuests are documented here.

## [Unreleased]

## [3.1.0] - 2026-09-07

- Added PlexonCore 1.x module registration for module `quests`, including `STARTING`, `READY`, `DEGRADED`, and `FAILED` lifecycle reporting and safe unregister on shutdown.
- Added an isolated optional Core bridge so compatible PlexonCore 1.x enables Core mode while a missing, disabled, unavailable, or incompatible Core safely falls back to standalone compatibility mode where possible.
- Added Core-aware provider discovery hints without replacing PlexonQuests' quest-specific integration adapters or their exact event/API contract validation.
- Added live `PLEXON_CORE` diagnostics showing Core plugin/API version, supported API range, module registration state, and `CORE`/`STANDALONE` mode.
- Preserved PlexonQuests-owned SQLite/WAL storage, rotations, assignments, rewards, GUI engine, PlaceholderAPI expansion, configuration system, public API, player data, natural-block tracking, and integration event semantics.
- Updated the build and tag-driven release workflows to compile against the exact PlexonCore 1.0.0 release as a Maven `provided` dependency and reject artifacts that shade a second Core runtime tree.
- Added automated Core lifecycle/compatibility/duplicate-registration coverage plus single-contribution regression tests for PlexonRanks rankup, PlexonCrates crate-open, and PlexonDailyRewards claim events.
- Updated operational documentation for Core mode, standalone mode, live upgrade, rollback, staging, diagnostics, and performance verification.

## [3.0.0] - 2026-09-06

- Added first-party integration adapters with verified PlexonRanks, PlexonCrates, and PlexonDailyRewards event conversion, durable source tokens, and immediate rank-slot refresh.
- Added immutable contribution metadata and metadata-aware filters for ranks, tools, keys, crates, shops, and daily rewards.
- Added strict validation for unsupported integration filters; filtered objectives fail closed when required metadata is absent.
- Added SAFE, LEGACY, and whitelisted MINIMESSAGE PlaceholderAPI rendering modes while preserving literal insertion for untrusted runtime values.
- Added MiniMessage candidate validation before atomic configuration activation.
- Updated diagnostics to surface provider/API detail and corrected PlexonCrates public event discovery.
- Preserved the indexed objective pipeline, durable contribution-token reservation, SQLite/WAL persistence, and guarded reserve/deliver/commit rewards.
- Added 3.0 integration/filter/API regression tests and updated build artifacts to PlexonQuests-3.0.0.

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
