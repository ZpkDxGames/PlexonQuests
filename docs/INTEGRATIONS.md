# PlexonQuests 3.0 integrations

PlexonQuests observes committed gameplay events. It does not parse commands, chat, inventory titles, holograms, lore, or another plugin's database.

## Integration contract

| Provider | Objective types | 3.0 bridge |
| --- | --- | --- |
| PlexonRanks | `PLEXON_RANK_UP` | `PlexonRankupEvent`; rank IDs and transaction ID are mapped, source token is `rankup:<transactionId>`, and rank slots refresh immediately. |
| PlexonTools | `PLEXON_TOOL_LEVEL_UP`, `PLEXON_TOOL_PROGRESS` | Public level/progress events are required. If the installed build has no supported public event classes, the adapter remains unavailable rather than inferring progress. |
| PlexonKeys | `PLEXON_KEY_EARN`, `PLEXON_KEY_CLAIM` | Public earn/claim events are required. No item-name, lore, or material detection is used. |
| PlexonCrates | `PLEXON_CRATE_OPEN` | `com.antondev.crates.api.event.CrateOpenEvent`; `OpeningPlan.transactionId()` provides durable deduplication. |
| PlexonShops | `PLEXON_SHOP_VISIT`, `PLEXON_SHOP_RATE`, `PLEXON_SHOP_CREATE` | Public visit/rate/create events are required. Missing provider APIs fail closed. |
| PlexonDailyRewards | `PLEXON_DAILY_REWARD_CLAIM` | `DailyRewardClaimedEvent`; tier/day/forced metadata is recorded after the provider commits delivery. |

The current source audit for the 3.0.0 build found verified event surfaces in PlexonRanks, PlexonCrates, and PlexonDailyRewards. The current `main` sources of PlexonTools, PlexonKeys, and PlexonShops do not expose the required public progress events, so those adapters intentionally report unavailable until a provider build adds the contract. This is preferable to false quest progress.

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

Player names, IDs, counts, transaction IDs, and other runtime values continue to use literal component insertion and are never reparsed as MiniMessage.
