# Backup, migration, and recovery

PlexonQuests stores durable state in `plugins/PlexonQuests/plexonquests.db` by default. SQLite WAL mode may also create `-wal` and `-shm` companions while Paper is running. Never copy only the main database file from a busy server.

## Online backup

Run:

```text
/quests backup
```

The command first flushes dirty assignment progress, checkpoints through the storage writer, and uses SQLite `VACUUM INTO` to create a consistent timestamped file in `plugins/PlexonQuests/backups/`. The destination is constrained to that directory and an existing backup is never overwritten.

Retain backups outside the game host according to the server's own disaster-recovery policy. Test restoration on a staging server.

## Cold backup

1. Stop Paper cleanly and wait for the shutdown message confirming persistence.
2. Verify the Java process has exited.
3. Copy the entire `plugins/PlexonQuests/` directory, including YAML, definitions, database, and backups.
4. Keep filesystem ownership and permissions suitable for the server account.

If the process was killed or crashed, copy the database together with any `plexonquests.db-wal` and `plexonquests.db-shm` files before attempting repair. Work only on a copy.

## Restore

1. Stop Paper.
2. Preserve the current `plugins/PlexonQuests/` directory as a rollback copy.
3. Place the chosen backup at the configured `storage.file` path. Remove stale WAL/SHM companions only while the server is stopped and only after preserving them with the rollback copy.
4. Ensure the restored configuration/definitions are compatible with the plugin version.
5. Start Paper, then run `/quests validate` and `/quests diagnostics`.
6. Check a representative player's active assignments, objective progress, history, and pin.

Do not import tables piecemeal unless you understand all assignment, objective, transaction, history, and profile invariants.

## Schema migrations

Migrations run transactionally on the single storage thread before profiles load.

| Schema | Change |
| --- | --- |
| 1 | Profiles, frozen assignments/objectives, claim/reroll transactions, history, admin audit, and rotation state |
| 2 | Hashed external contribution-token deduplication and retention index |

Both `PRAGMA user_version` and `schema_meta` record the supported schema. A database with a newer schema is rejected at startup rather than downgraded. Always create a backup before installing a plugin version that changes the schema.

## Interrupted reward claims

The claim sequence is preflight, durable reservation, delivery, then durable commit. A crash between reservation and a confirmed commit is intentionally ambiguous: an external command/economy/provider may have applied a side effect that cannot be safely queried or reversed.

At startup, every transaction still in `RESERVED` becomes `UNCERTAIN`. The assignment remains `CLAIMING`, so it cannot be claimed again automatically. `/quests diagnostics` reports the count.

For each uncertain claim:

1. Take an online or cold backup.
2. Correlate the transaction ID from player/admin reports and logs with the reward provider's audit trail.
3. Determine whether every planned reward was delivered. Do not infer from the assignment state alone.
4. Preserve the evidence and decision in the server's operator log.
5. If provider evidence is inconclusive, prefer avoiding automatic redelivery; a manual compensating grant is auditable and limits duplicate side effects.

PlexonQuests deliberately does not auto-resolve or auto-redeliver uncertain transactions. Direct SQL edits on a live server are unsupported. If database-level reconciliation is required, stop the server, work from a backup copy, and validate referential/transaction state before replacement.

## Writer queue or flush failures

- Run `/quests diagnostics` and inspect queue depth, rejected writes, dirty assignment count, and last flush result.
- Check free disk space, filesystem permissions, storage latency, and whether another process has opened the database for writing.
- Stop accepting gameplay traffic before queue saturation continues.
- Use `/quests save` only after the underlying storage fault is corrected.
- Shut Paper down cleanly. The plugin stops accepting new operations, submits one final dirty batch, checkpoints WAL, and waits up to `storage.shutdown-timeout`.

If final persistence times out, preserve the database plus WAL/SHM files before another start.

## Corrupt definition snapshots or origin data

An unreadable frozen assignment snapshot is logged with its assignment UUID and quarantined from the loaded profile; it is not reinterpreted using a newer YAML definition. Restore from backup or perform an offline, evidence-backed repair.

Chunk origin data is bounded and checksummed by the platform container. Missing, corrupt, overflowed, or explicitly disabled origin tracking is treated as unknown, which does not satisfy `NATURAL_ONLY`. This can undercount after data loss but cannot enable place/break farming.

## Retention

History older than `history-retention-days` and rows beyond `history-maximum-per-player` are removed at startup and daily. Hashed contribution tokens expire after 30 days. These deletions are intentional and not recoverable without a backup.
