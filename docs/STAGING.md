# Live staging checklist

Automated tests cover state transitions, serialization, weighted selection, reset boundaries/DST, brewing extraction, SQLite restart/idempotency/claim recovery, MockBukkit startup, PlexonCore module lifecycle/compatibility, and critical provider-event single-contribution paths. Complete this checklist on a real Paper 26.2 server before publishing 3.1.0.

Record the plugin commit, Paper build, Java build, PlexonCore version, plugin versions, configuration bundle, tester, date, and evidence link for every run.

## 3.1.0 upgrade preparation

- [ ] Start from a staging copy of the real PlexonCraft `plugins/PlexonQuests/` folder and 3.0.0 data/database.
- [ ] Back up `PlexonQuests-3.0.0.jar` and the full `plugins/PlexonQuests/` directory.
- [ ] Do not delete, regenerate, or relocate the Quests data folder.
- [ ] Confirm the staged 3.1.0 JAR checksum matches the CI/release checksum.

## Core present — mandatory

Use:

```text
PlexonCore-1.0.0.jar
PlexonQuests-3.1.0.jar
```

Run:

```text
/plexon modules
/plexon integrations
/plexon diagnostics
/quests validate
/quests diagnostics
/quests
```

Expected:

- [ ] PlexonQuests is registered as module `quests`, not shown only as legacy/standalone.
- [ ] Core API version is compatible with `>=1.0 <2.0`.
- [ ] Module state reaches `READY` after Quests startup.
- [ ] `/quests diagnostics` exposes `PLEXON_CORE` with version/API/range/module state and `CORE` mode.
- [ ] No private player state appears in Core diagnostics.

## Core absent — mandatory

Temporarily remove PlexonCore from a separate staging instance while keeping `PlexonQuests-3.1.0.jar` and the same production-like Quests data.

Expected:

- [ ] PlexonQuests starts normally in standalone compatibility mode.
- [ ] `/quests` works.
- [ ] `/quests diagnostics` reports Core unavailable/standalone.
- [ ] Quest progress and claims work.
- [ ] No `NoClassDefFoundError` or `ClassNotFoundException` for Core API classes occurs.

Restore PlexonCore after this test.

## Core quest behavior

- [ ] Confirm all default quests, pools, rarities, menus, messages, and effects validate.
- [ ] Join, receive daily/weekly/milestone assignments, reopen journal, filter, paginate, inspect details, pin, and change settings.
- [ ] Exercise every core objective listener in survival; confirm creative/spectator and cancelled events do not count by default.
- [ ] Break natural blocks and verify intended natural progress.
- [ ] Place then break player blocks and verify `NATURAL_ONLY` does not count them. Unload/reload chunks and restart.
- [ ] Kill mobs, fish, craft, smelt, enchant, brew, travel, change worlds, and accrue playtime.
- [ ] Complete `ALL`, `ANY`, and `SEQUENCE` assignments.
- [ ] Claim item/XP rewards, double-click claim, disconnect during preparation, fill inventory, and restart after reservation.
- [ ] Reroll free/paid-disabled paths; cancel confirmation and verify no mutation.
- [ ] Pin/unpin and verify history behavior.
- [ ] Restart across daily and weekly boundaries in the configured timezone.
- [ ] Run `/quests save`, `/quests backup`, restore that backup on a separate staging instance, and compare profiles.
- [ ] Stop during active progress and confirm shutdown flush/checkpoint behavior.

All of the above must match 3.0.0 player-facing semantics.

## Representative survival stack

- [ ] PlaceholderAPI placeholders in chat/scoreboard/tab and neutral offline behavior.
- [ ] Verify the `%plexonquests_*%` expansion is registered once in Core mode.
- [ ] Vault economy paid reroll and money claim success/failure/insufficient funds/refund.
- [ ] LuckPerms permanent/timed permission delivery and provider disable during claim.
- [ ] PlexonRanks category mapping, one bonus per configured category, rank-up contribution, transaction ID preservation, source-token dedupe, and immediate slot refresh.
- [ ] PlexonCrates crate-open event received once with crate/key/reward metadata and duplicate callback rejection.
- [ ] PlexonDailyRewards successful committed claim contribution once with tier/day metadata and duplicate callback rejection.
- [ ] PlexonTools, PlexonKeys, and PlexonShops remain fail-closed unless their exact supported public event/API contract is available.
- [ ] Protection plugin cancelled break/place/craft/inventory flows.
- [ ] Custom tools and multi-block mining: count intended committed blocks once and preserve origin protection.
- [ ] Multiworld included/excluded pool and quest eligibility.

## Reload tests — mandatory

Run:

```text
/quests reload
/plexon reload
```

After each command verify:

- [ ] Quests GUI opens normally.
- [ ] Core module registration/state remains correct.
- [ ] Integration listeners remain single.
- [ ] PAPI expansion remains single.
- [ ] Maintenance/storage tasks are not duplicated.
- [ ] Rankup/crate/daily events still progress exactly once.
- [ ] A rejected `/quests reload` candidate keeps the prior live configuration and does not mark an otherwise healthy Core module failed.

PlexonCore 1.0.0 reloads its configuration without replacing its API/registry objects; Quests should not need polling or a second registration loop.

## Restart / persistence — mandatory

Perform at least two full restarts with Core present.

Verify after each restart:

- [ ] Same active quests and objective progress.
- [ ] Same pins and history.
- [ ] Same completed totals and reward state.
- [ ] Same durable contribution dedupe state.
- [ ] Core module `quests` registers exactly once per boot.
- [ ] No duplicate-registration warning is emitted.

## Failure and abuse cases

- [ ] Repeated command and GUI click spam respects cooldowns and cannot bypass permission checks.
- [ ] Malformed quest/pool/menu edit reports the exact path and keeps the last good snapshot.
- [ ] Provider disabled after preflight produces rollback or an honest uncertain claim, never silent success.
- [ ] Replayed external source token before and after restart does not add progress twice.
- [ ] Slow or full storage surfaces queue/flush diagnostics and does not execute database work in event handlers.
- [ ] Corrupt/truncated assignment snapshot is quarantined; corrupt/missing origin state fails closed.
- [ ] History retention and daily maintenance stay within configured bounds.
- [ ] No player-controlled value injects MiniMessage click/hover/format tags.
- [ ] Incompatible Core is reported honestly and does not falsely become `READY` Core mode.

## Performance evidence

- [ ] Run every scenario in [PERFORMANCE.md](PERFORMANCE.md) against a control build.
- [ ] Compare representative Core-present and Core-absent runs.
- [ ] Save median/p95/p99/worst MSPT, event rate, queue maximum, memory/allocation evidence, and profiler output.
- [ ] Confirm no per-event Core service lookup/registry scan appears in hot paths.
- [ ] Agree and record the release budget; investigate every regression over it.

## Production upgrade after staging passes

1. Stop the server.
2. Keep `PlexonCore-1.0.0.jar`.
3. Remove `PlexonQuests-3.0.0.jar`.
4. Add `PlexonQuests-3.1.0.jar`.
5. Keep `plugins/PlexonQuests/` unchanged.
6. Start the server.
7. Run `/plexon modules`, `/plexon diagnostics`, `/quests validate`, and `/quests diagnostics`.

## Rollback

If staging or production validation fails:

1. Stop the server.
2. Restore `PlexonQuests-3.0.0.jar`.
3. Keep the existing `plugins/PlexonQuests/` folder/data.
4. Start the server and run Quests validation/diagnostics.

3.1.0 must not introduce a destructive database migration that blocks this rollback.

## Sign-off

- [ ] No unresolved `UNCERTAIN` claim remains unexplained.
- [ ] Backup restore was proven, not only created.
- [ ] Core-present and Core-absent tests passed.
- [ ] Two-restart persistence test passed.
- [ ] No duplicate listener/scheduler/PAPI/progress path was observed.
- [ ] Commands, permissions, configuration, API, recovery, Core integration, and migration docs match the staged build.
- [ ] CI artifact checksum matches the installed JAR.
- [ ] `v3.1.0` and the GitHub release are created only after all mandatory staging gates above pass.
