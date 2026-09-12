# PlexonQuests 4.1.0-rc.1

This release candidate is the first full-revamp candidate after the accepted 4.0.0 stable closure.

## Player-facing fixes

- Fixed configured MiniMessage such as `<aqua>2,500 experience points` being shown literally in the Phase 3 Quest Journal.
- Aligned reward, objective, description, history, and completion-message presentation with the plugin's MiniMessage contract.
- Replaced the journal's duplicate hardcoded progress bar with the shared configurable progress renderer from `menus.yml`.
- Improved low-progress visibility: non-zero progress below one full segment now renders one filled segment, while 0% remains empty and 100% remains fully filled.
- Normalized objective presentation to use parsed Adventure components instead of treating configured display markup as plain placeholder text.
- Cleaned compatibility/history reward presentation so persisted internal reward IDs are not exposed as player-facing prefixes.
- Removed the compatibility quest-card hint for shift-left reroll because the hardened interaction policy intentionally rejects shift clicks; rerolls remain available from quest details.

## Reliability and GUI safety

- Both inventory GUI families now use the shared `MenuInteractionRouter` policy.
- Semantic GUI actions accept ordinary left/right clicks only; unsafe shift, number-key, off-hand, drop, double-click, creative/middle, border, and unknown variants remain cancelled without dispatch.
- Inventory-open/close/replace actions are deferred out of `InventoryClickEvent` and guarded against stale holders and disconnected players.
- Existing reward, reroll, tracking, persistence, objective indexing, provenance, and integration authorities remain unchanged.
- Build and release provenance now identify stable `v4.0.0` as the 4.1 rollback baseline.

## Verification

The pre-RC presentation baseline passed Java 25 / Paper 26.2 verification with 134 tests, 0 failures, 0 errors, and 0 skipped tests. The exact RC source must pass the same canonical build and distribution gates before publication.

Runtime certification on the real PlexonCraft Paper 26.2 host remains required before stable 4.1.0 promotion. This prerelease is intended for that certification.

Rollback baseline: `v4.0.0` (`57a235460e5e936ca931ee8d0e96b0a424f134f8`).
