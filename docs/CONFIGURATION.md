# Configuration reference

All YAML files use `schema-version: 1`. PlexonQuests installs missing defaults without overwriting existing files. `/quests reload` parses a complete candidate snapshot off-thread, validates activation requirements, then swaps the immutable snapshot as one operation. If activation fails, the last good snapshot stays active.

Definition errors include an exact file/key path. An invalid quest or pool is quarantined where possible; startup/reload is rejected if no usable quest, rotating pool, rarity, or valid menu layout remains.

## Files

| Path | Purpose |
| --- | --- |
| `config.yml` | Rotation, slots, rerolls, tracking, storage, claims, feedback, and security |
| `messages.yml` | MiniMessage player/admin text and neutral placeholder value |
| `menus.yml` | Inventory titles, sizes, content slots, navigation, and item templates |
| `effects.yml` | Sound, title, bossbar, actionbar, and particle presets |
| `rarities.yml` | Rarity display, color, icon, glow, order, and default completion effect |
| `quests/**/*.yml` | One quest definition per file |
| `pools/*.yml` | Weighted daily/weekly selection pools |

Paths are resolved inside `plugins/PlexonQuests`; configuration and backup paths cannot escape that directory.

## Menu layout upgrades

PlexonQuests 2.0 uses `layout-version: 2` in `menus.yml`. On the first 2.0 startup, a layout-1 menu is copied to `plugins/PlexonQuests/backups/menus-v1-<timestamp>.yml` and the bundled layout-2 menu is installed atomically. Reapply intentional menu customizations to the new structure after comparing that backup. Reload validation rejects missing, out-of-bounds, duplicate, and overlapping menu slots.

## Root settings

### `rotation`

- `timezone`: IANA zone such as `America/Sao_Paulo`.
- `daily-reset`: local `HH:mm` boundary.
- `weekly-reset-day` and `weekly-reset-time`: weekly boundary.
- `completed-claim-grace`: how long completed assignments remain claimable after rotation.
- `recent-history-exclusion`: default historical exclusion window.

Period keys are based on the local period start date. DST gaps and overlaps are resolved by Java's timezone rules, and the persisted assignment remains authoritative after restart.

### `assignments` and `rank-progression`

- Base and maximum daily/weekly slots are bounded integers.
- `maximum-active-manual` caps manual assignments.
- `allow-one-action-to-progress-multiple-quests` controls fan-out from one accepted event.
- Rank categories map category IDs to progression indices. Bonuses apply per configured category, never per formatted rank prefix or chat text.
- If the supported PlexonRanks service is unavailable and `fallback-permissions` is true, numbered slot permissions are used.

A pool's `base-assignments` is the base for that pool; global maxima, rank bonuses, numbered permissions, and bypass behavior are still enforced.

### `rerolls`

Configure free daily/weekly counts, a per-period maximum, and optional Vault pricing. A replacement is completely selected and eligibility-checked before confirmation. The old assignment is not cancelled and no money is withdrawn until confirmation. Payment or activation failure rolls the database transaction back and attempts compensation.

### `tracking`

- Creative and spectator progress are disabled by default.
- `natural-block-mode`: `PERSISTENT_CHUNK`, `SESSION`, or `OFF`. `OFF` and unknown origin data fail closed for natural-only filters.
- `natural-block-maximum-positions-per-chunk`: hard memory/serialization bound.
- Travel sampling excludes teleports and caps implausible deltas.
- Actionbar/bossbar values are lower bounds on update intervals.
- AFK timeout gates sampled playtime.

### `storage`

SQLite uses WAL, a single bounded writer, batched dirty progress, periodic checkpoints, and a shutdown deadline. Retention maintenance runs at startup and daily. Reducing history limits deletes old history on the next maintenance pass; take a backup first.

### `claims`

- `manual-by-default`: default policy for content authors.
- `overflow-policy`: `CANCEL` preflights inventory capacity; `DROP` drops overflow at the player.
- `reservation-timeout`: operational reservation intent; interrupted reservations are conservatively marked uncertain at startup.

### `feedback`, `security`, and `diagnostics`

Feedback controls join reminders, pin presentation, thresholds, and throttles. Security bounds command/menu frequency, numbered permission scanning, and serialized item size. Diagnostics toggles sampling details; it does not place database work on gameplay event handlers.

## Quest definition

Quest IDs, objective IDs, pool IDs, and reward IDs use lowercase letters, numbers, underscores, or hyphens. Increment `revision` when the meaning changes. Active assignments keep the frozen snapshot they received.

```yaml
schema-version: 1
id: stonebound
revision: 1
enabled: true
scope: DAILY
category: mining
rarity: COMMON
weight: 10

eligibility:
  required-permission: ""
  blocked-permissions: []
  rank-categories: []
  worlds: [world]
  required-integrations: []

display:
  name: "<gradient:#56B9F2:#92E1FF><bold>Stonebound</bold></gradient>"
  short-description: "<gray>Mine natural stone"
  lore-template: default-quest-card
  icon:
    material: STONE_PICKAXE
    amount: 1
    glow-when-complete: true

completion-mode: ALL
claim-mode: MANUAL

objectives:
  natural_stone:
    type: BREAK_BLOCK
    amount: 750
    display: "<gray>Natural stone <current>/<required>"
    filters:
      materials: [STONE, DEEPSLATE, TUFF]
      origin: NATURAL_ONLY
      worlds: [world]
      game-modes: [SURVIVAL]

rewards:
  mode: ALL
  entries:
    experience:
      type: EXPERIENCE_POINTS
      amount: 350
      display: "<aqua>350 experience points"

effects:
  complete: quest-complete-common
  claim: quest-claim-common
```

### Quest scopes and completion

- `DAILY` and `WEEKLY` must belong to same-scope pools.
- `MILESTONE` is assigned when eligible and is not period-rotated.
- `MANUAL` is assigned through the API/admin command and uses a unique period key.
- Completion modes are `ALL`, `ANY`, and ordered `SEQUENCE`.
- Claim modes are `MANUAL` and `AUTOMATIC`.

### Objective types

Core listeners support `BREAK_BLOCK`, `PLACE_BLOCK`, `KILL_ENTITY`, `DAMAGE_ENTITY`, `HARVEST_CROP`, `CATCH_FISH`, `CRAFT_ITEM`, `SMELT_ITEM`, `ENCHANT_ITEM`, `BREW_POTION`, `PLAY_TIME`, `TRAVEL_DISTANCE`, `VISIT_WORLD`, `COMPLETE_ADVANCEMENT`, and `QUESTS_CLAIMED`.

External types are `PLEXON_RANK_UP`, `PLEXON_TOOL_LEVEL_UP`, `PLEXON_TOOL_PROGRESS`, `PLEXON_KEY_EARN`, `PLEXON_KEY_CLAIM`, `PLEXON_CRATE_OPEN`, `PLEXON_SHOP_VISIT`, `PLEXON_SHOP_RATE`, `PLEXON_SHOP_CREATE`, and `PLEXON_DAILY_REWARD_CLAIM`. Dependent definitions should declare the matching required integration.

Filters can restrict materials/caught materials, entity types, damage causes, spawn reasons, game modes, worlds/environments, movement types, advancements, required/blocked permissions, origin, maturity, hostile/unique contributions, teleports, minimum/maximum contribution, and per-objective cooldown.

### Rewards

`rewards.mode` is `ALL` or deterministic `ONE_OF`. Supported types are `ITEM`, `EXPERIENCE_POINTS`, `EXPERIENCE_LEVELS`, `MONEY`, `COMMAND`, `PERMISSION`, `PLEXON_KEY`, `MESSAGE`, `SOUND`, and `EFFECT`. `PLAYER_COMMAND` is deliberately rejected at claim preflight. Commands are single-line templates with `{player}` and `{uuid}` substitutions; only trusted administrators should edit reward definitions.

Item rewards use a Bukkit material or a Base64 serialized item bounded by `maximum-serialized-item-bytes`. Money requires Vault economy, permission rewards require LuckPerms, and Plexon key rewards require a configured safe fallback command when no supported delivery service is available.

## Pool definition

```yaml
schema-version: 1
id: daily
enabled: true
scope: DAILY
base-assignments: 3
prevent-duplicates: true
recent-history-exclusion: 7d

eligibility:
  required-permissions: []
  blocked-permissions: []
  rank-categories: []
  worlds: [world]
  excluded-worlds: [creative]
  required-integrations: []

mix:
  guaranteed-categories: [gathering]
  maximum-per-category: 2
  minimum-per-rarity:
    RARE: 1
  maximum-per-rarity:
    LEGENDARY: 1

quests:
  stonebound: 12
  timber-trail: 12
```

Weights must be positive and every referenced quest must exist, survive validation, and match the pool scope. Rarity constraints must name configured rarities. Floors are attempted before guaranteed categories and normal weighted selection; eligibility/category/cap conflicts can make a floor impossible, in which case selection continues without inventing an ineligible quest.

## MiniMessage safety

Configuration-authored strings may contain MiniMessage formatting. Runtime values such as player names, counts, objective text, provider details, and transaction IDs are inserted as literal components, not reparsed as markup. Keep click/hover actions in trusted configuration only.
