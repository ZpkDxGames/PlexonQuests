# PlexonQuests Phase 3 — Player UX / GUI Product Overhaul

## Dependency and freeze boundary

This UX layer is stacked on the accepted Pipeline 1 runtime/event head:

`38be2038e3c2674724665dbe3cc5e93b7af619f9`

The UX branch must not rewrite `CoreObjectiveListener`, `ProgressService`, `AssignmentService`, `AssignmentStateMachine`, `RewardService`, `StorageService`, Core/local/shadow provenance, persistence, or reward transaction semantics.

## Product hierarchy

The player-facing journal is one coherent family:

1. Quest Journal Home
2. Active Quests
3. Eligible Quests
4. Completed Quests
5. Tracked Quest
6. Quest Details
7. Help
8. Reroll confirmation only when a real reroll is prepared

Categories, scope and discovery state are filters/context rather than permanent top-level destinations. Registry health, cache diagnostics, storage internals and prerequisite graph diagnostics remain outside the normal player journal.

## Shared navigation

Comparable 54-slot screens keep the product controls stable:

- 45 — Previous when applicable
- 46 — state filter or contextual reroll action when applicable
- 47 — scope filter when applicable
- 48 — Back / Journal Home
- 49 — primary action when applicable
- 50 — secondary action / category context when applicable
- 51 — page or state
- 52 — Close
- 53 — Next when applicable

The top navigation uses Home, Active, Eligible, Completed, Tracked and Help. Irrelevant controls are omitted rather than rendered as dead buttons.

## Navigation context

`JournalNavigationContext` carries the exact origin view, page, discovery filter, category and scope. Opening Details from `Eligible / Mining / page 2` therefore returns to that exact origin rather than reconstructing a generic journal parent.

## View-model boundary

The player renderer derives bounded immutable presentation data from the current authoritative in-memory quest/profile snapshot. New product primitives include:

- `JournalNavigationContext`
- `JournalViewModel`
- `JournalNextAction`
- `QuestProgressPresentation`
- `QuestStatePresentation`

State, progress, display name, prerequisite display names, reward summary, tracking and available actions are resolved once per meaningful open/refresh where practical. Collections are filtered, sorted and paginated once before rendering the current page.

## State language

Player language deliberately differs from backend enum names:

- `AVAILABLE` → **ELIGIBLE**
- `COMPLETABLE` / completed assignment awaiting claim → **READY TO CLAIM**
- tracked assignment → **Status: ACTIVE** plus **Tracking: TRACKED**
- claimed historical entry → **COMPLETED**
- `DISABLED` definitions are hidden from normal discovery

The normal journal does not display raw internal quest IDs, permission nodes, registry/cache diagnostics or backend stale-state enum names.

## Eligible semantics

There is no fabricated Accept action. A definition marked Eligible means it can participate in its actual assignment mechanism:

- Daily — may be assigned by the daily rotation
- Weekly — may be assigned by the weekly rotation
- Milestone — starts automatically when its requirements are met
- Assigned — assigned by server staff

Prerequisite IDs are resolved into player-facing quest names. Other eligibility failures are translated into player language rather than exposing permission nodes or integration status enums.

## Progress presentation

Progress uses a fixed 10-segment representation such as:

`■■■■■■□□□□ 60%`

with the authoritative raw ratio below it, such as `24 / 40`. Display percentage is clamped to 0–100. Active cards show the most useful incomplete objective plus `+N more` for multi-objective quests; Details provides the objective breakdown.

## Actions and authority

### Track / Untrack

Tracking is a convenience attribute of an Active quest. The GUI re-reads the current assignment and delegates mutation to `QuestTrackingService`.

### Claim

A Ready to Claim quest exposes one Claim Reward action. A lightweight per-menu submission gate prevents duplicate GUI submission, then the GUI re-reads authoritative state and calls `RewardService.claim`. Reward transaction/idempotency authority remains in `RewardService`.

### Reroll

Reroll is shown only for an active rotating quest when the existing feature and permission allow it. The preview states that current progress will be replaced and shows the exact free/paid cost presentation. `RerollService.prepare` selects/reserves the real replacement; the 27-slot confirmation shows the replacement, exact cost and progress-loss warning before `RerollService.confirm`. The GUI does not implement its own economy or replacement transaction.

## Completed/history

Completed history uses the same visual language as the journal. `StorageService.history` is fetched asynchronously. The callback validates that the player is still online and still viewing the exact same holder before mutating only the current content region on the primary thread. No inventory click blocks on SQLite/storage work.

## Performance safeguards

The Phase 3 journal adds no per-player repeating task and no tick-based menu refresh. It does not persist data for cosmetic rendering, repeatedly sort unchanged lists, repeatedly expand PlaceholderAPI across every card, or create an unbounded menu cache. Static filler/navigation templates are simple and bounded; dynamic content is rendered only on meaningful opens, page/filter changes or authoritative action refreshes.

## Compatibility

- no database migration
- no quest-definition schema migration
- no public PlexonCore API change
- existing assignment/progress/reward semantics preserved
- existing Phase 2 menu/message resources are not destructively reset
- legacy command aliases remain compatible, while primary journal presentation uses Active / Eligible / Tracked / Ready to Claim / Completed terminology
- admin diagnostics remain in the existing administrative surfaces

## Runtime validation plan

1. Open `/quests` and verify Journal Home next-action priority.
2. Open Active Quests and verify active/ready states and progress.
3. Open Eligible Quests and verify Eligible/Locked filters.
4. Verify locked prerequisites resolve to human quest names.
5. Verify Daily/Weekly/Milestone/Assigned mechanism wording.
6. Open Tracked with and without a tracked active quest.
7. Track and Untrack from Quest Details.
8. Complete a quest and verify Ready to Claim.
9. Rapid-click Claim and verify at most one reward claim.
10. Open Completed and verify asynchronous history presentation.
11. Verify multi-objective progress and `+N more` summary.
12. From a paged/category/scope-filtered Eligible view, open Details and Back; confirm exact context restoration.
13. Change quest state while Details is open and verify stale action refresh language.
14. Rapid-click Track/Untrack and verify no duplicate mutation.
15. Verify cancelled block-break events do not progress quests.
16. Verify a normal Core-authoritative block fact increments once.
17. Exercise Tools + Skills + Jobs + Quests during combined mining.
18. Reconnect and re-open the journal.
19. Restart normally and verify quest/progress/history persistence.
20. Profile repeated Home/list/page/detail/history navigation with Spark/MSPT.
21. Include the plugin in the later integrated >=30-minute ecosystem soak.

Runtime certification remains pending. No Phase 3 merge, RC publication or stable promotion is authorized by this document.
