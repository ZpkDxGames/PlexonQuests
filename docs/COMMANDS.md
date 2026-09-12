# Commands and permissions — PlexonQuests 4.2

`/quest`, `/pquests`, and `/plexonquests` are aliases of `/quests`.

## Player commands

| Command | Permission | Result |
| --- | --- | --- |
| `/quests` | `plexonquests.use` | Quest Board home |
| `/quests current` / `/quests active` | `plexonquests.use` | Current quest or legacy overflow list |
| `/quests available` | `plexonquests.use` | All current quest offers |
| `/quests daily` | `plexonquests.use` | Daily offers |
| `/quests weekly` | `plexonquests.use` | Weekly offers |
| `/quests milestones` | `plexonquests.use` | Eligible Milestone offers |
| `/quests completed` / `/quests history` | `plexonquests.history` | Paginated persisted history |
| `/quests skills` / `/quests stats` | `plexonquests.use` | Optional PlexonSkills context |
| `/quests help` | `plexonquests.use` | Join/limit/budget/abandon help |
| `/quests track <assignment|quest>` | `plexonquests.pin` | Compatibility tracking |
| `/quests untrack` | `plexonquests.pin` | Clear tracking |
| `/quests reroll` | `plexonquests.reroll` | Compatibility/deprecation redirect to Available Quests |

Joining/abandoning use guarded GUI actions backed by the same `QuestParticipationService`; the GUI is not an assignment authority.

## Administrative compatibility

The 4.1 administrative surface remains available: `admin`, `reload`, `validate`, `assign`, `cancel`, `progress`, `complete`, `reset`, `rotate`, `save`, `backup`, and `diagnostics`. Destructive targeted mutations keep their repeat-to-confirm state guard.

`plexonquests.bypass.active-limit` allows authorized flows to exceed the normal active cap. Existing eligibility/reroll-cost/slot-limit bypasses remain. Daily/Weekly numbered slot permissions now influence period participation budget through `SlotResolver`; they do not make multiple normal quests run simultaneously.
