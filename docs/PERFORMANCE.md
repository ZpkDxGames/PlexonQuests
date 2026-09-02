# Performance model and profiling

PlexonQuests is designed around bounded in-memory matching and asynchronous persistence. Performance claims should be based on measurements from the target server stack; no document can substitute for a live Paper profile.

## Hot-path model

- Active objective handles are indexed per player by objective type, then by material or entity type where possible.
- Gameplay events inspect only matching buckets; they do not scan the full quest registry.
- Progress mutation is in memory on the primary thread and records one compact dirty snapshot per assignment.
- One bounded storage executor batches assignment updates and serializes all SQLite access.
- Natural-block origin checks use chunk-local encoded positions. No database query occurs per block break/place.
- Travel/playtime are sampled, and bossbar/actionbar updates are throttled.
- Source-token calls perform an intentional asynchronous database reservation because cross-restart idempotency is more important than accepting duplicate external transactions.

## Bounds and controls

| Resource | Bound/control |
| --- | --- |
| Storage queue | `storage.queue-capacity` |
| Dirty progress latency | `storage.flush-interval` |
| Origin positions per chunk | `tracking.natural-block-maximum-positions-per-chunk` |
| Serialized reward item | `security.maximum-serialized-item-bytes` |
| History | retention days and maximum rows per player |
| Contribution tokens | SHA-256 only, 30-day retention |
| External in-memory token cache | 2,048 entries per online player, one-hour window |
| GUI/menu size | 9–54 slots, validated |

Increasing a bound trades memory or shutdown work for capacity. Avoid treating a larger queue as a fix for persistent disk latency.

## Required benchmark method

Record all of the following with each result:

- exact Paper build, Java build/flags, CPU, memory, storage type, OS, and plugin commit;
- complete plugin list and relevant configuration changes;
- player/bot count, active assignments/objectives per player, world/chunk pattern, and event rate;
- warm-up duration, measured duration, sample count, median, p95, p99, and worst tick/MSPT;
- profiler link or exported report and the comparable no-PlexonQuests/control run.

Recommended scenarios:

1. 50, 100, and 200 players breaking mixed natural/placed blocks concurrently.
2. Mixed combat, crafting, fishing, travel, and playtime events with multiple active quests.
3. Rotation boundary with cold history reads and assignment insertion.
4. Claim bursts with items, XP, economy, permissions, commands, full inventory, and provider failure.
5. Maximum bounded registry/history and repeated chunk origin load/unload.
6. Slow-disk injection until queue pressure is visible, followed by clean shutdown.

Use Paper timings or spark for live MSPT/call-tree evidence and Java Flight Recorder/async-profiler when allocation or lock evidence is needed. Measure the storage volume independently.

## Acceptance budget

Establish the actual budget for the target network before release. A useful starting gate is:

- no unbounded growth in queue, dirty snapshots, token caches, or origin sets;
- no SQLite calls in core Bukkit gameplay listener stacks;
- no new sustained p95 MSPT regression greater than the network's agreed budget under the representative mixed scenario;
- rotation and claim bursts drain without queue rejection;
- shutdown completes within `storage.shutdown-timeout` on healthy storage.

Do not claim zero MSPT impact. Commit benchmark reports under a separate dated staging artifact once measured; this repository does not fabricate production numbers from MockBukkit tests.
