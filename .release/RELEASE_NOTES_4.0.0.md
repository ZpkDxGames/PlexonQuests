# PlexonQuests 4.0.0

Stable GitHub repository closure of the accepted PlexonQuests Phase 2 / Phase 3 premium quest-journal line and final RC3 UX.

## Quest runtime and performance

- Per-player objective-interest indexes keep high-frequency listeners on type/material/entity fast gates before expensive context is acquired.
- Main-thread ordinary contributions remain direct; durable source-token contributions are reserved through persistence to reject duplicate external progress.
- Natural-block provenance remains fail-closed for unknown/player-placed origin where definitions require natural acquisition.
- PlexonCore 2 Runtime remains the shared block-acquisition/origin authority when compatible Core is active, with local/shadow fallbacks preserving existing semantics.
- SQLite remains WAL-backed with bounded/coalesced persistence rather than synchronous objective-path writes.

## Reward and persistence safety

- Claims remain guarded by preflight, `CLAIMING`, durable reservation, ordered delivery, durable completion and explicit uncertain-state handling.
- Duplicate live claims are rejected.
- Irreversible console-command and Plexon-key fallback rewards are delivered after reversible reward types.
- A transaction with observed side effects is never silently rolled back/redelivered; ambiguous completion is recorded as uncertain for operator recovery.
- Existing assignment snapshots, completion history, tracking preference and rotation state remain compatible with the 3.3.1 data line.

## Premium journal UX

- Final 54-slot journal geometry uses Back/Home at 45, Previous at 48, page/context at 49, Next at 50 and the primary contextual action at 53.
- Slot 4 remains the section identity anchor while Home, Active, Eligible, Completed, Tracked and Help navigation stays explicit.
- Player-facing state, prerequisite, objective, reward and completion language avoids backend implementation terminology.
- Rapid journal clicks are bounded by debounce.
- Track/Untrack refreshes in place instead of reopening the inventory solely for presentation refresh.
- Completed-history loading remains asynchronous and stale-safe.

## Compatibility and distribution

- Paper 26.2 / Java 25.
- PlexonCore 2.0.4 is provisioned by exact SHA-256 and remains unshaded.
- SQLite JDBC is bundled.
- FastUtil is bundled only under the relocated `com.zpkdxgames.plexonquests.lib.fastutil` namespace.
- PlaceholderAPI, Bukkit/Paper, Adventure and optional integration API trees remain external/unshaded.

## Stable release verification

The exact final `main` source is rebuilt and tested before publication. The stable release includes:

- `PlexonQuests-4.0.0.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

The publisher downloads those assets after publication and verifies the SHA-256, exact source commit, final RC3 ancestry and stable non-prerelease boundary before completing.

Live PlexonCraft runtime/soak certification may remain `NOT_EXECUTED` in release provenance and is a deployment follow-up rather than a GitHub source/release blocker.

Rollback baseline: `v3.3.1` / `b74fc212d5aea41a0e01e9bc1bf5c5382302d014`.
