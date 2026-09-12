# 4.2 supersession note

The historical 4.0/4.1 Phase 3 journal below is retained as design context. PlexonQuests 4.2 supersedes its normal assignment/discovery flow with the explicit join-based Quest Board in [`PLAYER_UX_4.2.md`](PLAYER_UX_4.2.md), while retaining the mature persistence/transaction authorities.

---

# PlexonQuests Phase 3 — Player UX / GUI Product Overhaul

## Accepted boundary

Phase 3 is stacked on the accepted performance/runtime event lineage:

`38be2038e3c2674724665dbe3cc5e93b7af619f9`

The final premium journal candidate is PR #12 head:

`7bffcc3b9168fd22408805fb018d8a3f430784c3`

Stable `4.0.0` preserves that runtime/event architecture. The journal layer does not replace `CoreObjectiveListener`, `ProgressService`, `AssignmentService`, `AssignmentStateMachine`, `RewardService`, `StorageService`, Core/local/shadow provenance, persistence or reward transaction semantics.

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

## Final shared navigation

The RC3 54-slot journal geometry is:

- 4 — section identity
- 45 — Back / Journal Home
- 48 — Previous when applicable
- 49 — page/context
- 50 — Next when applicable
- 53 — primary contextual action when applicable

Other contextual controls are screen-specific and must not displace those shared anchors. The top navigation uses Home, Active, Eligible, Completed, Tracked and Help. Irrelevant controls are omitted rather than rendered as dead buttons.

## Navigation context

`JournalNavigationContext` carries the exact origin view, page, discovery filter, category and scope. Opening Details from `Eligible / Mining / page 2` therefore returns to that exact origin rather than reconstructing a generic journal parent.

## View-model boundary

The player renderer derives bounded immutable presentation data from the current authoritative in-memory quest/profile snapshot. Product primitives include:

- `JournalNavigationContext`
- `JournalViewModel`
- `JournalNextAction`
- `QuestProgressPresentation`
- `QuestStatePresentation`

State, progress, display name, prerequisite display names, reward summary, tracking and available actions are resolved once per meaningful open/refresh where practical. Collections are filtered, sorted and paginated before rendering the current page.

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

Prerequisite IDs are resolved into player-facing quest names. Other eligibility failures are translated into player language rather than exposing provider/internal status values.

## Progress presentation

Progress uses a fixed 10-segment representation such as `■■■■■■□□□□ 60%` with the authoritative raw ratio below it, such as `24 / 40`. Display percentage is clamped to 0–100. Active cards show the most useful incomplete objective plus `+N more` for multi-objective quests; Details provides the full objective breakdown.

## Actions and authority

### Track / Untrack

Tracking is a convenience attribute of an Active quest. The GUI re-reads the current assignment and delegates mutation to `QuestTrackingService`. RC3 refreshes the relevant inventory presentation in place rather than reopening the menu solely to reflect a Track/Untrack change.

### Claim

A Ready to Claim quest exposes one Claim Reward action. Bounded rapid-click/submission gating prevents duplicate GUI submission, then the GUI re-reads authoritative state and calls `RewardService.claim`. Reward transaction/idempotency authority remains in `RewardService`.

### Reroll

Reroll is shown only for an active rotating quest when the existing feature and permission allow it. `RerollService.prepare` selects/reserves the real replacement; confirmation shows the replacement, exact cost and progress-loss warning before `RerollService.confirm`. The GUI does not implement its own economy or replacement transaction.

## Completed/history

Completed history uses the same visual language as the journal. `StorageService.history` is fetched asynchronously. Completion validates that the player is still online and still viewing the expected holder before mutating the active content region on the primary thread. No inventory click blocks on SQLite/storage work and stale history work must not reopen or replace a newer view.

## Performance safeguards

The Phase 3 journal adds no per-player repeating task and no tick-based menu refresh. It does not persist data for cosmetic rendering, repeatedly sort unchanged lists, repeatedly expand PlaceholderAPI across every card or create an unbounded menu cache. Static templates are bounded; dynamic content renders on meaningful opens, page/filter changes or authoritative action refreshes.

RC3 also adds bounded rapid-click debounce so repeated UI clicks cannot fan out into uncontrolled action work.

## Compatibility

- no database migration solely for the Phase 3 UX
- no quest-definition schema migration solely for the journal layout
- no public PlexonCore API change
- existing assignment/progress/reward semantics preserved
- existing menu/message resources are not destructively reset
- legacy command aliases remain compatible while primary presentation uses Active / Eligible / Tracked / Ready to Claim / Completed terminology
- admin diagnostics remain in the existing administrative surfaces

## Runtime validation plan

1. Open `/quests` and verify Journal Home next-action priority.
2. Open Active Quests and verify active/ready states and progress.
3. Open Eligible Quests and verify Eligible/Locked filters.
4. Verify locked prerequisites resolve to human quest names.
5. Verify Daily/Weekly/Milestone/Assigned mechanism wording.
6. Open Tracked with and without a tracked active quest.
7. Track and Untrack from Quest Details and verify in-place refresh.
8. Complete a quest and verify Ready to Claim.
9. Rapid-click Claim/Track/Untrack and verify bounded single-authority handling.
10. Open Completed and verify asynchronous stale-safe history presentation.
11. Verify multi-objective progress and `+N more` summary.
12. From a paged/category/scope-filtered Eligible view, open Details and Back; confirm exact context restoration.
13. Change quest state while Details is open and verify stale action refresh language.
14. Verify cancelled block-break events do not progress quests.
15. Verify a normal Core-authoritative block fact increments once.
16. Exercise integrated Plexon objectives during combined activity.
17. Reconnect and reopen the journal.
18. Restart normally and verify quest/progress/history persistence.
19. Profile repeated Home/list/page/detail/history navigation with Spark/MSPT.
20. Include the plugin in an integrated soak when performing deployment certification.

Live runtime certification is a post-release operational follow-up. It does not block the verified GitHub stable source/release boundary.
