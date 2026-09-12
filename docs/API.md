# PlexonQuests 4.2 API compatibility note

4.2 changes normal **player participation UX**, not public API ownership. Existing public/admin manual assignment remains available and distinct from player Quest Board joins. Active assignment state remains the runtime/progress authority; an offer appearing in the catalog does not itself make that quest active.

PlexonSkills is consumed only through its optional public API/ServicesManager boundary and is not a mandatory eligibility dependency.

---

# Public API, events, and placeholders

PlexonQuests registers `PlexonQuestsAPI` with Bukkit's `ServicesManager`. Add PlexonQuests as a compile-time `provided` dependency in the consuming plugin and declare `softdepend: [PlexonQuests]` when the integration is optional.

PlexonQuests 3.1.0 keeps this API independently registered in both Core and standalone modes. Consumers do **not** need to access PlexonQuests through PlexonCore, and no public Quests API break is required by the Core migration.

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

## PlexonCore relationship in 3.1.0

When compatible PlexonCore 1.x is installed, PlexonQuests registers module `quests` with Core API range `>=1.0 <2.0`. Core provides shared module/ecosystem visibility; the Quests API remains the domain API for quest operations.

`integrationStates()` preserves the existing deep PlexonQuests provider states and additionally exposes `PLEXON_CORE`. That Core entry reports the installed Core version, API version, supported range, module registration state, and `CORE`/`STANDALONE` mode. The simpler Core provider registry does not replace Quests' exact integration-contract state.

PlexonCore absence does not remove `PlexonQuestsAPI`. If Quests starts successfully in standalone mode, the service is registered normally.

## Threading and data ownership

- Player-state operations return `CompletableFuture` and marshal themselves to the primary server thread.
- Completion callbacks are not guaranteed to run on your preferred executor; explicitly schedule Bukkit work where needed.
- `AssignmentView`, `ObjectiveView`, `QuestDefinitionView`, and `IntegrationView` are immutable snapshots and safe to retain.
- Definition lookup and integration-state maps are read-only snapshots.
- Manual assignment and journal opening currently require the target player to be online.
- Do not block the primary thread with `future.join()` or `get()`.
- Core adoption does not move player profiles, assignments, rewards, persistence, or quest-specific integration state into PlexonCore.

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
| `integrationStates()` | Read detected optional integration states and details, including `PLEXON_CORE` in 3.1.0 |

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

PlexonCore provider discovery does not change these semantics. Core availability is never treated as proof that a provider exposes the exact quest event/API contract.

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

The expansion identifier is `plexonquests` and persists through PlaceholderAPI reloads. Core mode does not register a second expansion; `%plexonquests_*%` remains owned by PlexonQuests.

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
