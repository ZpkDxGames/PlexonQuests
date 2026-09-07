# PlexonQuests 3.1.0 — PlexonCore integration

PlexonQuests 3.1.0 is the first Core-aware PlexonQuests release. It adopts PlexonCore 1.0.0 incrementally; the quest engine remains owned by PlexonQuests.

## Runtime modes

PlexonQuests declares `PlexonCore` as a soft dependency.

| Runtime | Result |
| --- | --- |
| PlexonCore 1.x installed and compatible | `CORE` mode; module `quests` registers with Core. |
| PlexonCore absent | `STANDALONE` mode; existing PlexonQuests behavior remains available. |
| PlexonCore API outside `>=1.0 <2.0` | Core registration is reported incompatible and Quests continues in standalone compatibility mode when linkage is safe. |
| PlexonCore installed but its API service cannot be resolved safely | Quests logs a concise warning and starts standalone. |

The optional boundary is isolated so a missing Core JAR does not eagerly link `com.zpkdxgames.plexoncore` classes during normal standalone startup.

## Module registration

Core module identity:

```text
Module ID: quests
Display name: PlexonQuests
Supported Core API: >=1.0 <2.0
```

Published capabilities:

```text
quest-engine
quest-rotations
quest-objectives
quest-rewards
quest-integrations
sqlite-persistence
minimessage
placeholderapi
```

Lifecycle is reported as:

```text
STARTING -> READY
          -> DEGRADED when a genuine Quests health condition requires it
          -> FAILED for a genuine startup/runtime failure
```

The module is unregistered only after Quests services and persistent state have been stopped/flushed.

## Ownership boundary

PlexonCore provides shared ecosystem infrastructure. PlexonQuests still owns:

- quest definitions, pools, rotations and assignments
- objective matching and contribution semantics
- milestones, manual quests, history, rerolls and pins
- claim transactions and reward delivery
- natural-block, playtime and travel tracking
- player profiles and the PlexonQuests SQLite database
- quest-specific integration adapters
- quest GUIs, commands, placeholders and configuration

3.1.0 does not migrate the database, data folder, GUI engine, scheduler ownership, reward engine or public Quests API into Core.

## Provider discovery

When Core is active, Quests consumes Core's broad provider state as a discovery hint:

```text
Core says missing/disabled -> Quests can skip redundant basic discovery
Core says present          -> Quests still verifies the exact supported event/API contract
Core has no provider entry -> Quests uses its existing discovery path
```

The existing Quests adapters remain authoritative for event classes, metadata extraction, source-token construction and fail-closed compatibility.

Quests 3.1.0 intentionally does not overwrite Core's globally keyed provider entries with quest-specific health. Core 1.0 `IntegrationRegistry.publish(...)` replaces a provider entry, so retaining the richer Quests state locally avoids clobbering another module's ecosystem view.

## Diagnostics

`/plexon modules` should show PlexonQuests as module `quests` instead of a legacy standalone Plexon plugin when compatible Core is active.

`/quests diagnostics` retains its existing deep quest/storage/integration information and includes a `PLEXON_CORE` state with:

- Core plugin version
- Core API version
- supported range `>=1.0 <2.0`
- module ID/state
- `CORE` or `STANDALONE` mode

Core diagnostics supplement `/quests diagnostics`; they do not replace it.

## Reload behavior

`/quests reload` reloads PlexonQuests only. A rejected candidate configuration leaves the current live configuration and module registration intact. A successful integration refresh updates Core health detail when the module is already healthy.

PlexonCore 1.0.0's `/plexon reload` reloads Core configuration without replacing the API service, module registry or integration registry objects, so the Quests bridge remains valid across that operation.

## Build dependency

PlexonCore is a Maven `provided` dependency. CI and the tag release workflow download the exact `v1.0.0` release artifact, verify SHA-256

```text
4abce6de93293e21b31cb874734430d5bdc77de17a4c3b98fd6a9006e1f13018
```

and install it into the workflow-local Maven repository before `mvn -B -ntp clean verify`.

The distribution check fails if the Quests JAR contains a `com/zpkdxgames/plexoncore/` package tree. The runtime Core implementation must come from the installed `PlexonCore-1.0.0.jar`.

## Live upgrade

1. Stop the server.
2. Back up `PlexonQuests-3.0.0.jar` and `plugins/PlexonQuests/`.
3. Keep `PlexonCore-1.0.0.jar` installed.
4. Replace only the Quests JAR with `PlexonQuests-3.1.0.jar`.
5. Do **not** delete or regenerate `plugins/PlexonQuests/`.
6. Start the server.
7. Run `/plexon modules`, `/plexon diagnostics`, `/quests validate` and `/quests diagnostics`.
8. Confirm normal quest progression and claim behavior before reopening the server broadly.

## Rollback

3.1.0 performs no destructive data migration. If rollback is required:

1. Stop the server.
2. Restore `PlexonQuests-3.0.0.jar`.
3. Keep the existing `plugins/PlexonQuests/` data folder.
4. Start the server and run the normal Quests validation/diagnostics checks.

The intended player-facing result of 3.1.0 is unchanged quest behavior with Core module visibility underneath.
