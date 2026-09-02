# Commands and permissions

`/quest`, `/pquests`, and `/plexonquests` are aliases of `/quests`. Player commands recheck permission at execution time and GUI actions recheck permission when clicked. A short configurable command cooldown protects against accidental or scripted spam.

Assignment arguments accept a one-based visible slot number, a unique assignment UUID prefix, or a quest ID when exactly one visible assignment matches. Omitting the argument for `claim` or `reroll` prefers the pinned assignment, then the first compatible visible assignment.

## Player commands

| Command | Permission | Result |
| --- | --- | --- |
| `/quests` | `plexonquests.use` | Open the complete journal |
| `/quests daily` | `plexonquests.use` | Open daily assignments |
| `/quests weekly` | `plexonquests.use` | Open weekly assignments |
| `/quests milestones` | `plexonquests.use` | Open milestone assignments |
| `/quests pinned` | `plexonquests.pin` | Open the pinned quest or journal |
| `/quests history` | `plexonquests.history` | Open paginated personal history |
| `/quests claim [assignment]` | `plexonquests.claim` | Claim a completed assignment |
| `/quests reroll [assignment]` | `plexonquests.reroll` | Open the reroll confirmation menu |
| `/quests settings` | `plexonquests.settings` | Configure feedback preferences |
| `/quests info` | none | Show configured plugin information |

## Administrative commands

Console can use commands that do not open an inventory. Player-targeting administrative commands currently require the target to be online and their profile to be ready.

| Command | Permission | Result |
| --- | --- | --- |
| `/quests admin` | `plexonquests.admin.gui` | Open the administrator quest browser |
| `/quests reload` | `plexonquests.admin.reload` | Validate a candidate configuration and activate it atomically |
| `/quests validate` | `plexonquests.admin.validate` | Print active definition issues with exact paths |
| `/quests assign <player> <quest>` | `plexonquests.admin.assign` | Assign a `MANUAL` quest |
| `/quests cancel <player> <assignment>` | `plexonquests.admin.cancel` | Cancel and archive an assignment |
| `/quests progress <player> <assignment> <objective> <add\|set> <amount>` | `plexonquests.admin.progress` | Adjust objective progress and audit the change |
| `/quests complete <player> <assignment>` | `plexonquests.admin.complete` | Complete an active assignment |
| `/quests reset <player> <daily\|weekly\|milestone\|manual\|all>` | `plexonquests.admin.reset` | Cancel the selected assignments |
| `/quests rotate <daily\|weekly> [player\|all]` | `plexonquests.admin.rotate` | Force a fresh period selection for online targets |
| `/quests save` | `plexonquests.admin.save` | Flush dirty progress and checkpoint SQLite WAL |
| `/quests backup` | `plexonquests.admin.backup` | Create a consistent timestamped SQLite backup |
| `/quests diagnostics` | `plexonquests.admin.diagnostics` | Report profiles, indexes, queue state, origin sets, invalid definitions, integrations, and uncertain claims |

Administrative mutations are written to `admin_audit`. The parent `plexonquests.admin` permission grants all administrative child permissions and defaults to operators.

## Bypass and slot permissions

| Permission | Meaning | Default |
| --- | --- | --- |
| `plexonquests.bypass.eligibility` | Ignore permission, rank-category, and world eligibility; required integrations still cannot be bypassed | false |
| `plexonquests.bypass.reroll-cost` | Skip paid reroll cost | false |
| `plexonquests.bypass.slot-limit` | Use the configured maximum slot count | false |
| `plexonquests.slots.daily.<number>` | Fallback exact/minimum daily slot target, bounded by configuration | unset |
| `plexonquests.slots.weekly.<number>` | Fallback exact/minimum weekly slot target, bounded by configuration | unset |

Never grant broad wildcard permissions to untrusted users. Numbered permissions are searched only up to `security.maximum-numbered-permission` and the scope maximum.
