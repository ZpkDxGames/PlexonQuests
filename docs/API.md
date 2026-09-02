# Public API, events, and placeholders

PlexonQuests registers `PlexonQuestsAPI` with Bukkit's `ServicesManager`. Add PlexonQuests as a compile-time `provided` dependency in the consuming plugin and declare `softdepend: [PlexonQuests]` when the integration is optional.

## Obtaining the service

```java
RegisteredServiceProvider<PlexonQuestsAPI> registration =
        Bukkit.getServicesManager().getRegistration(PlexonQuestsAPI.class);
if (registration == null) {
    // PlexonQuests is missing, disabled, or did not start safely.
    return;
}
PlexonQuestsAPI quests = registration.getProvider();
```

The service is unregistered during disable, including partial-startup failure. Do not retain it after `PluginDisableEvent`.

## Threading and data ownership

- Player-state operations return `CompletableFuture` and marshal themselves to the primary server thread.
- Completion callbacks are not guaranteed to run on your preferred executor; explicitly schedule Bukkit work where needed.
- `AssignmentView`, `ObjectiveView`, `QuestDefinitionView`, and `IntegrationView` are immutable snapshots and safe to retain.
- Definition lookup and integration-state maps are read-only snapshots.
- Manual assignment and journal opening currently require the target player to be online.
- Do not block the primary thread with `future.join()` or `get()`.

## Operations

| Method | Purpose |
| --- | --- |
| `activeAssignments(UUID)` | Read active assignment views |
| `assignment(UUID, UUID)` | Read one visible assignment |
| `questDefinition(String)` | Read one active definition |
| `assignManual(UUID, String)` | Assign an online player a `MANUAL` quest |
| `submitProgress(UUID, ExternalProgressContribution)` | Submit a supported external objective contribution |
| `isComplete` / `isClaimable` | Query assignment state |
| `pin` / `unpin` | Change the player's pin |
| `openJournal(UUID, String)` | Open `all`, `daily`, `weekly`, or `milestone` journal scope |
| `integrationStates()` | Read detected optional integration states and details |

## External progress

Use `submitProgress` only after the source plugin has committed its own successful transaction/event. Supported stable types are exposed by `ExternalObjectiveType`.

```java
ExternalProgressContribution contribution = new ExternalProgressContribution(
        ExternalObjectiveType.PLEXON_CRATE_OPEN,
        1L,
        true,
        crateTransactionId);

quests.submitProgress(playerId, contribution)
        .exceptionally(failure -> {
            getLogger().log(Level.WARNING, "Quest contribution failed", failure);
            return null;
        });
```

When the source system has a stable transaction/event ID, pass it as `sourceToken`. PlexonQuests hashes the compound player/type/token value and persistently reserves it before applying progress. Replays return without adding progress, including after restart. Tokens are limited to 256 characters and retained for 30 days; never place secrets or player-visible text in a token. An empty token has no cross-call idempotency guarantee.

The `unique` flag is available to objective filters; it is not a substitute for a source token.

## Bukkit events

All events are synchronous and expose identifiers rather than mutable internal assignment objects.

| Event | Timing and cancellation |
| --- | --- |
| `QuestAssignEvent` | Before durable insert; cancellable |
| `QuestAssignedEvent` | After durable insert |
| `QuestProgressEvent` | Before applying an accepted delta; cancellable and delta-adjustable |
| `QuestObjectiveCompleteEvent` | After an objective first completes |
| `QuestCompleteEvent` | After the assignment completes |
| `QuestPreClaimEvent` | Before reward reservation; cancellable |
| `QuestClaimedEvent` | After delivery and durable commit |
| `QuestClaimUncertainEvent` | Delivery may have occurred but durable outcome requires review |
| `QuestExpireEvent` | After expiration is queued for persistence |
| `QuestRerollEvent` | After replacement activation succeeds |

Handlers must remain fast. Do not perform network or database work in the event call; queue it and use the transaction/assignment identifiers for correlation.

## PlaceholderAPI

The expansion identifier is `plexonquests` and persists through PlaceholderAPI reloads.

| Placeholder | Value |
| --- | --- |
| `%plexonquests_active%` | Active assignment count |
| `%plexonquests_completed_unclaimed%` | Claimable count |
| `%plexonquests_completed_total%` | Persisted claimed total |
| `%plexonquests_daily_time_left%` | Time to daily reset |
| `%plexonquests_weekly_time_left%` | Time to weekly reset |
| `%plexonquests_daily_rerolls%` | Remaining free daily rerolls |
| `%plexonquests_pinned_name%` | Plain pinned quest name |
| `%plexonquests_pinned_progress%` | Current/required pinned progress |
| `%plexonquests_pinned_percentage%` | Integer pinned percentage |
| `%plexonquests_pinned_time_left%` | Pinned expiry countdown |
| `%plexonquests_slot_limit%` | Current daily slot limit |
| `%plexonquests_integration_<id>_status%` | Integration status enum |

Offline, not-ready, and off-primary-thread player requests return the configured neutral value instead of touching mutable Bukkit/player state.
