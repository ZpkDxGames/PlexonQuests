# Changelog

All notable changes to PlexonQuests are documented here.

## [Unreleased]

## [4.1.0] - 2026-09-12

- Final stable full-source revamp for Java 25, Paper 26.2 and PlexonCore 2.0.4.
- Hardened premium Quest Journal GUI interaction, formatting and progress presentation.
- Added server-wide objective-type fast rejection before detailed common-event routing while preserving per-player material/entity indexes.
- Reduced activity-sampler CPU for stationary and rejected outlier movement.
- Removed unnecessary concurrent-map and duplicate click-policy overhead from the compatibility GUI listener.
- Preserved the mature quest lifecycle, economy, persistence, provenance, prerequisite, rotation, reroll, integration and transaction authorities.
- Stable rollback baseline: `v4.0.0` (`57a235460e5e936ca931ee8d0e96b0a424f134f8`).

## [4.0.0] - Stable

- Promotes the accepted Phase 2 / Phase 3 premium quest-journal line and final RC3 UX to stable `4.0.0` without changing production Java behavior after the accepted candidate.
- Preserves indexed per-player objective interest, durable contribution-token deduplication, fail-closed origin/provenance handling, deterministic rotations, prerequisite-aware eligibility, asynchronous bounded history, and coalesced SQLite/WAL persistence.
- Preserves guarded reward claims with preflight, durable reservation, side-effect uncertainty recording, irreversible command/key delivery last, and durable completion.
- Preserves the final premium 54-slot journal geometry, explicit player-facing states, bounded rapid-click debounce, in-place Track/Untrack refresh, and stale-safe asynchronous Completed history rendering.
- Stable Build verification now proves the final RC3 head, accepted Phase 3/performance ancestry, Java 25/Paper 26.2/PlexonCore 2.0.4 compatibility, SQLite inclusion, relocated FastUtil, provided-dependency non-shading, non-empty/all-green tests, checksum and provenance.
- Replaces historical one-off/RC publishers with an exact-current-`main` stable publisher that rebuilds the source, publishes the JAR plus verification evidence, downloads the published assets and verifies their SHA-256 and exact source provenance before completion.
- Live PlexonCraft runtime certification remains a post-release deployment follow-up and may be recorded as `NOT_EXECUTED` in GitHub release provenance.
- Rollback baseline remains `v3.3.1` at `b74fc212d5aea41a0e01e9bc1bf5c5382302d014`.

## [3.3.0] - 2026-09-09

- Migrated high-frequency block-break acquisition to the PlexonCore 2 Runtime while preserving a small PlexonQuests MONITOR gate for final cancellation correctness.
- Added `AUTO`, `CORE`, `LOCAL`, and `SHADOW` runtime/origin modes with restart-only authority changes and safe fallback behavior.
- Extracted source-independent block objective processing so Core and local acquisition share the same objective-interest, crop-maturity, filter, and progression semantics.
- Adopted PlexonCore 2.0.1 shared block-origin authority with idempotent, persisted lazy import of the legacy PlexonQuests chunk-PDC provenance format.
- Kept legacy local provenance data intact for rollback; Core import is additive and `UNKNOWN` remains fail-closed for natural/player-placed objective filters.
- Disabled duplicate local provenance listeners/writes while Core is authoritative; LOCAL and SHADOW retain local provenance maintenance as required.
- Added SHADOW comparison counters so local provenance can remain authoritative while Core results are measured without duplicate quest contribution.
- Expanded `/quests diagnostics` with runtime mode, origin provider, runtime epoch, Core route/event/fallback counters, listener consolidation state, shadow mismatches, and origin-import state.
- Preserved local block-place objective handling and all non-block objective listeners because the released Core 2 API does not expose those acquisition routes.
- Updated Java 25/Paper 26.2 build and release workflows to provision the immutable PlexonCore 2.0.1 release by SHA-256, reject Core shading, verify the distribution, and enforce tag/project-version parity.
- Added Core/runtime migration documentation, rollback guidance, and candidate-versus-stable release gates.

## [3.2.0] - 2026-09-09

- Added per-player objective-interest indexes with type, material, and entity fast gates so irrelevant gameplay events bypass quest processing before expensive context or allocations are created.
- Removed completed and reroll-reserved objectives from active candidate indexes and precomputed requirement flags and cooldown keys used by hot contribution paths.
- Optimized block, crop, kill, damage, craft, brew, fish, smelt, enchant, world, and advancement listeners while preserving natural-block provenance bookkeeping and fail-closed semantics.
- Added lazy origin, crop-maturity, and spawn-reason lookups; global spawn-reason tracking now disables itself when no enabled definition can consume it.
- Reduced contribution-path overhead with direct synchronous handling for ordinary main-thread contributions and one monotonic-time snapshot per application.
- Reworked block-origin removal batching to use bounded, deduplicated chunk-grouped primitive position queues instead of unbounded per-block object lists.
- Changed travel/play-time sampling to iterate only players with matching active objective interest and retain fractional progress across scheduler refreshes.
- Moved actionbar/bossbar rendering behind throttle checks so suppressed feedback no longer pays formatting and component-building costs.
- Added in-flight profile-load deduplication to prevent duplicate storage reads and duplicate attach callbacks during login bursts.
- Added first-party Plexon integration fast gates, exact coalesced amount handling for PlexonTools progress events, and provider enable/disable lifecycle handling without duplicate listener registration.
- Added reload-safe maintenance and activity scheduler ownership so runtime configuration swaps replace tasks instead of stacking duplicate jobs.
- Preserved the existing bounded single-writer SQLite/WAL model, coalesced dirty-assignment persistence, claim transaction safety, quest semantics, GUI behavior, and public API contracts.
- Expanded regression coverage for no-interest listener paths, objective-interest indexing, provenance bookkeeping, scheduler behavior, and PlexonTools coalesced progress/multi-level event contracts.
- Updated release packaging for version-independent distribution verification and automated 3.2.0 JAR/checksum publication.

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