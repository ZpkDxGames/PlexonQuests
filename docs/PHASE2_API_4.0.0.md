# PlexonQuests 4.0 Phase 2 API

The Phase 2 API is additive. The existing `PlexonQuestsAPI` remains registered and is not replaced.

## Journal service

Retrieve `PlexonQuestsJournalAPI` from Bukkit `ServicesManager`.

It exposes asynchronous/main-thread-safe journal retrieval for an online loaded player:

- `journal(UUID)` — immutable views for all loaded quest definitions;
- `quest(UUID, String)` — one immutable journal view;
- `tracked(UUID)` — the current compatible pinned/tracked assignment;
- `prerequisiteGraph()` — immutable active prerequisite graph.

Offline or unloaded players return neutral/empty views. The API does not perform a synchronous SQLite query to materialize an offline player.

`JournalQuestView` contains quest ID, category, scope, derived `JournalState`, progress, tracked flag, prerequisites, and missing prerequisites. Collection members are defensive immutable copies.

## JournalState

`JournalState` is a presentation/progression projection, not a persistence replacement:

- `LOCKED`
- `AVAILABLE`
- `ACTIVE`
- `TRACKED`
- `COMPLETABLE`
- `COMPLETED`
- `COOLDOWN`
- `EXPIRED`
- `DISABLED`

The durable 3.3.1 `AssignmentState` remains authoritative for assignment and reward transactions.

## Tracking event

`QuestTrackEvent` fires synchronously after an active assignment is tracked or untracked through the Phase 2 journal. It includes:

- player;
- assignment UUID;
- quest ID;
- resulting tracked boolean.

Tracking continues to persist through the existing 3.x pinned-assignment preference field. Existing pin API methods and integrations remain compatible.

## PlaceholderAPI

The following Phase 2 aliases are additive:

| Phase 2 alias | Existing compatible source |
| --- | --- |
| `%plexonquests_active_count%` | `%plexonquests_active%` |
| `%plexonquests_completed_count%` | `%plexonquests_completed_total%` |
| `%plexonquests_tracked%` | `%plexonquests_pinned_name%` |
| `%plexonquests_tracked_progress%` | `%plexonquests_pinned_progress%` |
| `%plexonquests_tracked_percentage%` | `%plexonquests_pinned_percentage%` |
| `%plexonquests_tracked_time_left%` | `%plexonquests_pinned_time_left%` |

Resolution stays profile/cache based and returns the configured neutral placeholder when a player is offline, unloaded, or the resolver is invoked off the primary thread.

## Threading contract

Live Bukkit objects are never handed to the storage/config worker for Phase 2 analysis. Journal API requests invoked off-thread are marshalled to the primary thread before accessing online player/profile state. Completion history warming uses immutable history rows and updates a bounded cache asynchronously.

## Compatibility policy

4.0 does not expose mutable persistence records as public API. New public functionality is added through separate types/service registration rather than changing the signatures of `PlexonQuestsAPI` methods relied on by 3.x consumers.
