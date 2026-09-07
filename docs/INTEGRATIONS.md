# PlexonQuests 3.1 integrations

PlexonQuests observes committed gameplay events. It does not parse commands, chat, inventory titles, holograms, lore, or another plugin's database.

## PlexonCore discovery relationship

PlexonCore 1.x owns broad ecosystem discovery: whether a plugin is installed/enabled and what general state/version Core knows. PlexonQuests still owns the exact quest integration contract.

When Core mode is active:

```text
Core says provider missing
-> Quests can skip redundant basic plugin discovery

Core says provider ready/present
-> Quests still verifies the exact supported event/API classes

Core has no usable provider state
-> Quests uses its existing discovery behavior
```

A Core registry entry is never proof that a quest event contract is compatible. Quests adapters remain responsible for listener registration, metadata extraction, `Contribution` construction, objective-filter support, source-token generation, and fail-closed behavior.

Core 1.0 has globally keyed provider entries. PlexonQuests 3.1.0 therefore does not overwrite those entries with quest-specific health; doing so could erase richer state published by another module. Deep provider compatibility remains visible through `/quests diagnostics` and `PlexonQuestsAPI.integrationStates()`.

`PLEXON_CORE` is also exposed in the Quests integration-state view with Core version, API version, supported range `>=1.0 <2.0`, module registration state, and runtime mode.

## Integration contract

| Provider | Objective types | 3.1 bridge |
| --- | --- | --- |
| PlexonRanks | `PLEXON_RANK_UP` | `PlexonRankupEvent`; rank IDs and transaction ID are mapped, source token is `rankup:<transactionId>`, and rank slots refresh immediately. |
| PlexonTools | `PLEXON_TOOL_LEVEL_UP`, `PLEXON_TOOL_PROGRESS` | Public level/progress events are required. If the installed build has no supported public event classes, the adapter remains unavailable rather than inferring progress. |
| PlexonKeys | `PLEXON_KEY_EARN`, `PLEXON_KEY_CLAIM` | Public earn/claim events are required. No item-name, lore, or material detection is used. |
| PlexonCrates | `PLEXON_CRATE_OPEN` | `com.antondev.crates.api.event.CrateOpenEvent`; `OpeningPlan.transactionId()` provides durable deduplication. |
| PlexonShops | `PLEXON_SHOP_VISIT`, `PLEXON_SHOP_RATE`, `PLEXON_SHOP_CREATE` | Public visit/rate/create events are required. Missing provider APIs fail closed. |
| PlexonDailyRewards | `PLEXON_DAILY_REWARD_CLAIM` | `DailyRewardClaimedEvent`; tier/day/forced metadata is recorded after the provider commits delivery. |

The verified event surfaces inherited from the 3.0.0 production baseline are PlexonRanks, PlexonCrates, and PlexonDailyRewards. PlexonTools, PlexonKeys, and PlexonShops remain fail-closed when the installed provider build does not expose the required public event contract. Core adoption does not relax this rule.

## Supported filters

### PlexonRanks

- `from-rank`
- `to-rank`
- `from-category`
- `to-category`

Metadata keys: `rank.from`, `rank.to`, `rank.category.from`, `rank.category.to`, `rank.transaction`.

### PlexonTools

- `tool-id`
- `tool-category`
- `minimum-level`
- `progress-type`
- `material`

### PlexonKeys

- `key-category`
- `source`

### PlexonCrates

- `crate-id`
- `crate-category` (only when the provider event supplies it)
- `key-id`
- `reward-id`

### PlexonShops

- `shop-id`
- `shop-type`
- `minimum-rating`

### PlexonDailyRewards

- `tier-id`
- `day`

Unknown filters on integration objectives are validation errors. A filter whose required metadata is absent does not match.

## Duplicate-listener rule

Core mode does not register a second quest listener for provider events. The existing PlexonQuests adapters remain the single listener path. A rankup, crate-open, or daily-reward event must become exactly one accepted contribution before the normal source-token deduplication layer is considered.

The 3.1.0 regression suite fires synthetic versions of those three provider events and asserts exactly one contribution with the expected metadata/source token.

## PlaceholderAPI rendering

`config.yml` controls trusted PlaceholderAPI formatting:

```yaml
text:
  placeholder-rendering:
    default: SAFE
    allow-legacy: true
    allow-minimessage: true
```

- `<papi:identifier>` uses the configured default. `SAFE` is recommended.
- `<papi_legacy:identifier>` allows legacy color/decorative codes only through Adventure's legacy serializer.
- `<papi_mm:identifier>` allows colors, gradients, rainbow, basic decorations, and reset. Interactive tags such as click, hover, insertion, URL/command actions, selector, NBT, and score are escaped as literal text.

Player names, IDs, counts, transaction IDs, provider details, and other runtime values continue to use literal component insertion and are never reparsed as MiniMessage. PlexonCore adoption does not weaken this policy.
