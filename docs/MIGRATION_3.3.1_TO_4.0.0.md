# PlexonQuests 3.3.1 → 4.0.0 Migration

`4.0.0-rc.1` is an additive Phase 2 release candidate. Do not replace the production stable build without a backup and PlexonCraft staging validation.

## Preserved data

The 4.0 candidate deliberately retains the mature 3.3.1 SQLite assignment and transaction model. Existing assignment IDs, frozen definition snapshots, objective progress, claim identities, reroll history, player preferences, audit rows, and quest history remain readable. The new player-facing journal states are derived and are not written over the durable assignment-state column.

Quest prerequisite completion is reconstructed asynchronously from existing claimed history. No destructive schema rewrite is required for this RC.

## Before first 4.0 startup

1. Stop the server cleanly.
2. Copy the complete `plugins/PlexonQuests/` directory to an external backup location.
3. Preserve the 3.3.1 JAR and its checksum for rollback.
4. Verify the SQLite file and any sidecar WAL/SHM files are not being mutated by a running server.
5. Install `PlexonQuests-4.0.0-rc.1.jar` on staging first.
6. Keep PlexonCore 2.0.4 available when validating shared Runtime acquisition/provenance.

The built-in `/quests backup` remains useful, but an offline filesystem backup is the safest pre-upgrade boundary.

## Quest authoring compatibility

Existing 3.3.1 quest files remain valid. A quest can opt into Phase 2 prerequisites with:

```yaml
prerequisites:
  completed-quests:
    - first-steps
```

Prerequisite IDs are normalized to lowercase. Missing references, self-dependencies, cycles, duplicate quest IDs, and enabled quests that depend on disabled prerequisite quests are rejected.

Do not rename a quest ID that already has player history unless the corresponding progression migration has been planned; prerequisite completion identity is quest-ID based.

## First-start verification

After startup:

1. confirm the plugin reports version `4.0.0-rc.1`;
2. run `/quests validate`;
3. run `/quests diagnostics`;
4. open `/quests` and inspect Overview, Active, Available, Categories, Tracked, Completed, Statistics, and Help;
5. verify representative 3.3.1 active progress survived;
6. verify a previously claimed milestone satisfies any new prerequisite that references it;
7. verify an incomplete prerequisite remains `LOCKED`;
8. verify daily/weekly rotations do not duplicate assignments after reconnect or restart;
9. verify completed rewards remain single-claim;
10. restart and repeat the representative checks.

## Failure behavior

Malformed prerequisite graphs fail closed. Startup rejects an invalid initial graph. Runtime graph refresh keeps the previous valid Phase 2 graph and emits diagnostics instead of silently treating invalid dependencies as satisfied.

Malformed or unknown durable player state must be diagnosed and preserved for recovery; do not delete the database as a repair step.

## Rollback to 3.3.1

Because this RC does not replace the durable assignment-state model with Phase 2 journal states, rollback is intentionally straightforward:

1. stop Paper cleanly;
2. archive the failed 4.0 staging data directory for diagnosis;
3. restore the exact pre-upgrade `plugins/PlexonQuests/` backup;
4. restore the verified 3.3.1 JAR;
5. start Paper;
6. run `/quests validate` and `/quests diagnostics`;
7. verify representative player assignments/history.

Do not attempt an in-place downgrade over partially investigated data when a clean pre-upgrade backup is available.

## Stable-promotion gate

A successful migration alone does not authorize stable `v4.0.0`. Complete `RUNTIME_CERTIFICATION_4.0.0.md`, including objective/provenance, rewards, integrations, restart/reload, Spark/MSPT comparison, and the soak gate first.
