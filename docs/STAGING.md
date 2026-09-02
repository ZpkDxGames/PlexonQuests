# Live staging checklist

Automated tests cover state transitions, serialization, weighted selection, reset boundaries/DST, brewing extraction, SQLite restart/idempotency/claim recovery, and MockBukkit startup. Complete this checklist on a real Paper 26.2 server before publishing a release.

Record the plugin commit, Paper build, Java build, plugin versions, configuration bundle, tester, date, and evidence link for every run.

## Clean server

- [ ] Start on Java 25/Paper 26.2 with no optional plugins.
- [ ] Confirm all default quests, pools, rarities, menus, messages, and effects validate.
- [ ] Confirm missing optional integrations appear as `MISSING` and dependent content is excluded.
- [ ] Join, receive daily/weekly/milestone assignments, reopen journal, filter, paginate, inspect details, pin, and change settings.
- [ ] Exercise every core objective listener in survival; confirm creative/spectator and cancelled events do not count by default.
- [ ] Place then break blocks and verify `NATURAL_ONLY` does not count them. Unload/reload chunks and restart.
- [ ] Complete `ALL`, `ANY`, and `SEQUENCE` assignments.
- [ ] Claim item/XP rewards, double-click claim, disconnect during preparation, fill inventory, and restart after reservation.
- [ ] Reroll free/paid-disabled paths; cancel confirmation and verify no mutation.
- [ ] Restart across daily and weekly boundaries in the configured timezone.
- [ ] Run `/quests save`, `/quests backup`, restore that backup on a separate staging instance, and compare profiles.
- [ ] Stop during active progress and confirm shutdown flush/checkpoint behavior.

## Representative survival stack

- [ ] PlaceholderAPI placeholders in chat/scoreboard/tab and neutral offline behavior.
- [ ] Vault economy paid reroll and money claim success/failure/insufficient funds/refund.
- [ ] LuckPerms permanent/timed permission delivery and provider disable during claim.
- [ ] PlexonRanks category mapping, one bonus per configured category, rank-up contribution, missing/incompatible API state.
- [ ] Plexon DailyRewards successful committed claim contribution only.
- [ ] PlexonTools, PlexonKeys, PlexonCrates, and PlexonShops through verified public events/API or stable-token `submitProgress` adapters.
- [ ] Protection plugin cancelled break/place/craft/inventory flows.
- [ ] Custom tools and multi-block mining: count intended committed blocks once and preserve origin protection.
- [ ] Multiworld included/excluded pool and quest eligibility.

## Failure and abuse cases

- [ ] Repeated command and GUI click spam respects cooldowns and cannot bypass permission checks.
- [ ] Malformed quest/pool/menu edit reports the exact path and keeps the last good snapshot.
- [ ] Provider disabled after preflight produces rollback or an honest uncertain claim, never silent success.
- [ ] Replayed external source token before and after restart does not add progress twice.
- [ ] Slow or full storage surfaces queue/flush diagnostics and does not execute database work in event handlers.
- [ ] Corrupt/truncated assignment snapshot is quarantined; corrupt/missing origin state fails closed.
- [ ] History retention and daily maintenance stay within configured bounds.
- [ ] No player-controlled value injects MiniMessage click/hover/format tags.

## Performance evidence

- [ ] Run every scenario in [PERFORMANCE.md](PERFORMANCE.md) against a control build.
- [ ] Save median/p95/p99/worst MSPT, event rate, queue maximum, memory/allocation evidence, and profiler output.
- [ ] Agree and record the release budget; investigate every regression over it.

## Sign-off

- [ ] No unresolved `UNCERTAIN` claim remains unexplained.
- [ ] Backup restore was proven, not only created.
- [ ] Commands, permissions, configuration, API, recovery, and migration docs match the staged build.
- [ ] CI artifact checksum matches the installed JAR.
- [ ] Release tag and GitHub release are created only after explicit maintainer approval.
