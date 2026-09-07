from pathlib import Path


def read(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    text = read(path)
    actual = text.count(old)
    if actual != count:
        raise RuntimeError(f"{path}: expected {count} occurrence(s), found {actual}: {old[:100]!r}")
    write(path, text.replace(old, new, count))


# Version/build metadata.
replace("pom.xml", "  <version>2.0.1</version>", "  <version>3.0.0</version>")
replace("pom.xml", "<project.build.outputTimestamp>2026-09-01T00:00:00Z</project.build.outputTimestamp>",
        "<project.build.outputTimestamp>2026-09-06T00:00:00Z</project.build.outputTimestamp>")
replace(".github/workflows/build.yml", "name: PlexonQuests-2.0.1", "name: PlexonQuests-3.0.0")

# Immutable contribution metadata while preserving the 2.x constructor shape.
write("src/main/java/com/zpkdxgames/plexonquests/objective/Contribution.java", r'''package com.zpkdxgames.plexonquests.objective;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public record Contribution(
        ObjectiveType type,
        long amount,
        Material material,
        EntityType entityType,
        EntityDamageEvent.DamageCause damageCause,
        CreatureSpawnEvent.SpawnReason spawnReason,
        String world,
        World.Environment worldEnvironment,
        GameMode gameMode,
        boolean originKnown,
        boolean natural,
        boolean mature,
        boolean hostile,
        boolean teleport,
        boolean unique,
        String movementType,
        String advancementKey,
        Map<String, String> metadata,
        String sourceToken) {

    public Contribution {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    normalized.put(
                            key.trim().toLowerCase(Locale.ROOT),
                            Objects.requireNonNullElse(value, "").trim());
                }
            });
        }
        metadata = Map.copyOf(normalized);
        sourceToken = Objects.requireNonNullElse(sourceToken, "");
    }

    /** Compatibility constructor retained for core 2.x listeners and external source compatibility. */
    public Contribution(
            ObjectiveType type,
            long amount,
            Material material,
            EntityType entityType,
            EntityDamageEvent.DamageCause damageCause,
            CreatureSpawnEvent.SpawnReason spawnReason,
            String world,
            World.Environment worldEnvironment,
            GameMode gameMode,
            boolean originKnown,
            boolean natural,
            boolean mature,
            boolean hostile,
            boolean teleport,
            boolean unique,
            String movementType,
            String advancementKey,
            String sourceToken) {
        this(
                type, amount, material, entityType, damageCause, spawnReason, world, worldEnvironment, gameMode,
                originKnown, natural, mature, hostile, teleport, unique, movementType, advancementKey,
                Map.of(), sourceToken);
    }

    public static Contribution simple(ObjectiveType type, long amount, Player player) {
        return integration(type, amount, player, Map.of(), "");
    }

    public static Contribution integration(
            ObjectiveType type,
            long amount,
            Player player,
            Map<String, String> metadata,
            String sourceToken) {
        return new Contribution(
                type,
                amount,
                null,
                null,
                null,
                null,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                false,
                false,
                true,
                false,
                false,
                true,
                "",
                "",
                metadata,
                sourceToken);
    }
}
''')

replace(
    "src/main/java/com/zpkdxgames/plexonquests/service/ProgressService.java",
    '''                value.mature(), value.hostile(), value.teleport(), value.unique(), value.movementType(),
                value.advancementKey(), "");''',
    '''                value.mature(), value.hostile(), value.teleport(), value.unique(), value.movementType(),
                value.advancementKey(), value.metadata(), "");''')

# Public API: optional metadata without breaking the old four-argument constructor.
write("src/main/java/com/zpkdxgames/plexonquests/api/ExternalProgressContribution.java", r'''package com.zpkdxgames.plexonquests.api;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** A validated external contribution. Source tokens should be stable transaction/event IDs when available. */
public record ExternalProgressContribution(
        ExternalObjectiveType type,
        long amount,
        boolean unique,
        Map<String, String> metadata,
        String sourceToken) {
    public ExternalProgressContribution {
        type = Objects.requireNonNull(type, "type");
        if (amount <= 0L) {
            throw new IllegalArgumentException("Contribution amount must be positive");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    normalized.put(
                            key.trim().toLowerCase(Locale.ROOT),
                            Objects.requireNonNullElse(value, "").trim());
                }
            });
        }
        metadata = Map.copyOf(normalized);
        sourceToken = Objects.requireNonNullElse(sourceToken, "");
        if (sourceToken.length() > 256) {
            throw new IllegalArgumentException("Source token cannot exceed 256 characters");
        }
    }

    public ExternalProgressContribution(
            ExternalObjectiveType type, long amount, boolean unique, String sourceToken) {
        this(type, amount, unique, Map.of(), sourceToken);
    }

    public static ExternalProgressContribution of(ExternalObjectiveType type, long amount) {
        return new ExternalProgressContribution(type, amount, true, Map.of(), "");
    }
}
''')

replace(
    "src/main/java/com/zpkdxgames/plexonquests/api/PlexonQuestsAPIImpl.java",
    '''                        external.unique(),
                        "",
                        "",
                        external.sourceToken());''',
    '''                        external.unique(),
                        "",
                        "",
                        external.metadata(),
                        external.sourceToken());''')

# Metadata-aware matcher and strict integration filter validation.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/objective/matcher/ObjectiveMatcher.java",
    '''        if (!filters.advancementKeys().isEmpty()
                && !filters.advancementKeys().contains(contribution.advancementKey())) {
            return 0L;
        }
        for (String permission : filters.requiredPermissions()) {''',
    '''        if (!filters.advancementKeys().isEmpty()
                && !filters.advancementKeys().contains(contribution.advancementKey())) {
            return 0L;
        }
        if (!IntegrationObjectiveFilters.matches(objective.type(), filters.extras(), contribution.metadata())) {
            return 0L;
        }
        for (String permission : filters.requiredPermissions()) {''')

replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/DefinitionLoader.java",
    '''import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;''',
    '''import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import com.zpkdxgames.plexonquests.objective.matcher.IntegrationObjectiveFilters;''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/DefinitionLoader.java",
    '''            ObjectiveFilters filters = parseFilters(objective.getConfigurationSection("filters"), path + "." + id + ".filters");
            output.put(id, new ObjectiveDefinition(id, type, amount, objective.getString("display", id), filters));''',
    '''            ObjectiveFilters filters = parseFilters(objective.getConfigurationSection("filters"), path + "." + id + ".filters");
            IntegrationObjectiveFilters.validate(type, filters.extras());
            output.put(id, new ObjectiveDefinition(id, type, amount, objective.getString("display", id), filters));''')

# Placeholder rendering configuration.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/PluginSettings.java",
    '''        Feedback feedback,
        Security security,
        Diagnostics diagnostics) {''',
    '''        Feedback feedback,
        Security security,
        Text text,
        Diagnostics diagnostics) {''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/PluginSettings.java",
    '''    public record Security(
            long commandCooldownMillis,
            long guiClickCooldownMillis,
            int maximumNumberedPermission,
            int maximumSerializedItemBytes) {}

    public record Diagnostics(boolean debugTiming, int timingSampleRate) {}''',
    '''    public record Security(
            long commandCooldownMillis,
            long guiClickCooldownMillis,
            int maximumNumberedPermission,
            int maximumSerializedItemBytes) {}

    public enum PlaceholderRenderingMode {
        SAFE,
        LEGACY,
        MINIMESSAGE
    }

    public record Text(
            PlaceholderRenderingMode defaultPlaceholderRendering,
            boolean allowLegacy,
            boolean allowMiniMessage) {}

    public record Diagnostics(boolean debugTiming, int timingSampleRate) {}''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/PluginSettings.java",
    '''        Diagnostics diagnostics = new Diagnostics(
                yaml.getBoolean("diagnostics.debug-timing", false),
                positive(yaml, "diagnostics.timing-sample-rate", 1000));''',
    '''        Text text = new Text(
                enumValue(
                        PlaceholderRenderingMode.class,
                        yaml.getString("text.placeholder-rendering.default"),
                        PlaceholderRenderingMode.SAFE),
                yaml.getBoolean("text.placeholder-rendering.allow-legacy", true),
                yaml.getBoolean("text.placeholder-rendering.allow-minimessage", true));

        Diagnostics diagnostics = new Diagnostics(
                yaml.getBoolean("diagnostics.debug-timing", false),
                positive(yaml, "diagnostics.timing-sample-rate", 1000));''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/PluginSettings.java",
    '''        return new PluginSettings(rotation, assignments, rank, rerolls, tracking, storage, claims, feedback, security, diagnostics);''',
    '''        return new PluginSettings(rotation, assignments, rank, rerolls, tracking, storage, claims, feedback, security, text, diagnostics);''')

replace(
    "src/main/resources/config.yml",
    '''integrations:
  placeholderapi: AUTO
  vault: AUTO
  luckperms: AUTO
  plexonranks: AUTO
  plexontools: AUTO
  plexonkeys: AUTO
  plexoncrates: AUTO
  plexonshops: AUTO
  plexondailyrewards: AUTO

feedback:''',
    '''integrations:
  placeholderapi: AUTO
  vault: AUTO
  luckperms: AUTO
  plexonranks: AUTO
  plexontools: AUTO
  plexonkeys: AUTO
  plexoncrates: AUTO
  plexonshops: AUTO
  plexondailyrewards: AUTO

text:
  placeholder-rendering:
    # SAFE inserts PlaceholderAPI output as literal text. LEGACY and MINIMESSAGE are opt-in trusted modes.
    default: SAFE
    allow-legacy: true
    allow-minimessage: true

feedback:''')

# SAFE / LEGACY / whitelisted MiniMessage PlaceholderAPI rendering.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/presentation/TextService.java",
    '''import com.zpkdxgames.plexonquests.config.ConfigManager;
import java.lang.reflect.InvocationTargetException;''',
    '''import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.PluginSettings.PlaceholderRenderingMode;
import java.lang.reflect.InvocationTargetException;''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/presentation/TextService.java",
    '''import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;''',
    '''import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/presentation/TextService.java",
    '''import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;''',
    '''import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/presentation/TextService.java",
    '''    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();''',
    '''    private static final Pattern MINI_TAG = Pattern.compile("<(/?)([^<>]+)>");
    private static final Set<String> TRUSTED_TAGS = Set.of(
            "color", "gradient", "rainbow", "bold", "b", "italic", "i", "underlined", "u",
            "strikethrough", "st", "reset", "black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white");

    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/presentation/TextService.java",
    '''        if (player != null && placeholderApiAvailable()) {
            resolvers.add(TagResolver.resolver("papi", (arguments, context) -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                return Tag.inserting(Component.text(applyPlaceholderApi(player, "%" + identifier + "%")));
            }));
        }''',
    '''        if (player != null && placeholderApiAvailable()) {
            var rendering = configs.snapshot().settings().text();
            resolvers.add(TagResolver.resolver("papi", (arguments, context) -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                return Tag.inserting(renderPlaceholder(
                        player, identifier, rendering.defaultPlaceholderRendering()));
            }));
            resolvers.add(TagResolver.resolver("papi_legacy", (arguments, context) -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                PlaceholderRenderingMode mode = rendering.allowLegacy()
                        ? PlaceholderRenderingMode.LEGACY
                        : PlaceholderRenderingMode.SAFE;
                return Tag.inserting(renderPlaceholder(player, identifier, mode));
            }));
            resolvers.add(TagResolver.resolver("papi_mm", (arguments, context) -> {
                String identifier = arguments.popOr("Expected a PlaceholderAPI identifier").value();
                PlaceholderRenderingMode mode = rendering.allowMiniMessage()
                        ? PlaceholderRenderingMode.MINIMESSAGE
                        : PlaceholderRenderingMode.SAFE;
                return Tag.inserting(renderPlaceholder(player, identifier, mode));
            }));
        }''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/presentation/TextService.java",
    '''    private boolean placeholderApiAvailable() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private String applyPlaceholderApi(Player player, String placeholder) {''',
    '''    private Component renderPlaceholder(Player player, String identifier, PlaceholderRenderingMode mode) {
        String value = applyPlaceholderApi(player, "%" + identifier + "%");
        return switch (mode) {
            case SAFE -> Component.text(value);
            case LEGACY -> legacy.deserialize(value);
            case MINIMESSAGE -> miniMessage.deserialize(whitelistTrustedMiniMessage(value));
        };
    }

    private static String whitelistTrustedMiniMessage(String value) {
        Matcher matcher = MINI_TAG.matcher(value == null ? "" : value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String body = matcher.group(2).trim();
            int colon = body.indexOf(':');
            String name = (colon < 0 ? body : body.substring(0, colon)).toLowerCase(Locale.ROOT);
            boolean hex = name.matches("#[0-9a-f]{6}");
            boolean allowed = hex || TRUSTED_TAGS.contains(name);
            String replacement = allowed ? matcher.group() : "\\" + matcher.group();
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private boolean placeholderApiAvailable() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private String applyPlaceholderApi(Player player, String placeholder) {''')

# Candidate validation before atomic config activation.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java",
    '''        List<String> activationErrors = new ArrayList<>();
        validateMenus(menus, activationErrors);
        QuestRegistrySnapshot registry = new DefinitionLoader(''',
    '''        List<String> activationErrors = new ArrayList<>();
        validateMenus(menus, activationErrors);
        MiniMessageValidator.validateYaml("messages.yml", messages, activationErrors);
        MiniMessageValidator.validateYaml("menus.yml", menus, activationErrors);
        MiniMessageValidator.validateYaml("effects.yml", effects, activationErrors);
        QuestRegistrySnapshot registry = new DefinitionLoader(''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java",
    '''        registry = withPoolCapacityWarnings(registry, settings);
        if (registry.quests().isEmpty()) {''',
    '''        registry = withPoolCapacityWarnings(registry, settings);
        MiniMessageValidator.validateRegistry(registry, activationErrors);
        if (registry.quests().isEmpty()) {''')

# Adapter registry and verified Crates public event class.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/integration/IntegrationManager.java",
    '''import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.ProgressService;''',
    '''import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.rotation.RotationService;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/integration/IntegrationManager.java",
    '''    public void registerProgressBridges(ProgressService progress) {
        registerPlayerEvent(
                "PlexonRanks",
                "com.zpkdxgames.plexonranks.event.PlexonRankupEvent",
                ObjectiveType.PLEXON_RANK_UP,
                progress);
        registerPlayerEvent(
                "PlexonDailyRewards",
                "com.zpkdxgames.plexondailyrewards.event.DailyRewardClaimedEvent",
                ObjectiveType.PLEXON_DAILY_REWARD_CLAIM,
                progress);
    }''',
    '''    public void registerProgressBridges(
            ProgressService progress,
            ProfileService profiles,
            RotationService rotations,
            ConfigManager configs) {
        IntegrationContext context = new IntegrationContext(plugin, configs, progress, profiles, rotations);
        PlexonIntegrationAdapters.all().forEach(adapter -> adapter.register(context));
    }''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/integration/IntegrationManager.java",
    '"com.antondev.crates.event.PlexonCrateOpenedEvent"',
    '"com.antondev.crates.api.event.CrateOpenEvent"')

# Fix the adapter helper signature after the source file was introduced.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/integration/PlexonIntegrationAdapters.java",
    '''        final void warnUnavailable(IntegrationContext context, ReflectiveOperationException | LinkageError exception) {''',
    '''        final void warnUnavailable(IntegrationContext context, Throwable exception) {''')

replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''            integrations.registerProgressBridges(progress);''',
    '''            integrations.registerProgressBridges(progress, profiles, rotations, configs);''')

# Surface bridge details in diagnostics without exposing secrets.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/command/QuestCommand.java",
    '''                                + (integration.detectedVersion().isBlank()
                                        ? "" : " <dark_gray>v" + integration.detectedVersion()))));''',
    '''                                + (integration.detectedVersion().isBlank()
                                        ? "" : " <dark_gray>v" + integration.detectedVersion())
                                + (integration.detail().isBlank()
                                        ? "" : " <dark_gray>• <gray>" + integration.detail()))));''')

# Release notes and operator-facing documentation.
replace(
    "CHANGELOG.md",
    '''## [Unreleased]\n\n## [2.0.1] - 2026-09-05''',
    '''## [Unreleased]\n\n## [3.0.0] - 2026-09-06\n\n- Added first-party integration adapters with verified PlexonRanks, PlexonCrates, and PlexonDailyRewards event conversion, durable source tokens, and immediate rank-slot refresh.\n- Added immutable contribution metadata and metadata-aware filters for ranks, tools, keys, crates, shops, and daily rewards.\n- Added strict validation for unsupported integration filters; filtered objectives fail closed when required metadata is absent.\n- Added SAFE, LEGACY, and whitelisted MINIMESSAGE PlaceholderAPI rendering modes while preserving literal insertion for untrusted runtime values.\n- Added MiniMessage candidate validation before atomic configuration activation.\n- Updated diagnostics to surface provider/API detail and corrected PlexonCrates public event discovery.\n- Preserved the indexed objective pipeline, durable contribution-token reservation, SQLite/WAL persistence, and guarded reserve/deliver/commit rewards.\n- Added 3.0 integration/filter/API regression tests and updated build artifacts to PlexonQuests-3.0.0.\n\n## [2.0.1] - 2026-09-05''')

replace("README.md", "Copy `PlexonQuests-2.0.1.jar`", "Copy `PlexonQuests-3.0.0.jar`")
replace(
    "README.md",
    '''| PlexonRanks | Rank-category slot bonuses and rank-up progress |\n| Plexon DailyRewards | Successful daily-reward claim progress |\n| PlexonTools, PlexonKeys, PlexonCrates, PlexonShops | Compatibility status and eligibility gating; progress must use a verified public event/API or `submitProgress` |''',
    '''| PlexonRanks | Rank-category slot bonuses, rank metadata filters, immediate slot refresh, durable rank-up progress |\n| PlexonCrates | Crate-open progress from the public `CrateOpenEvent`, including transaction-safe deduplication and crate/key/reward metadata |\n| Plexon DailyRewards | Successful claim progress with tier/day metadata and duplicate-resistant daily source tokens |\n| PlexonTools, PlexonKeys, PlexonShops | Adapter-ready; activates only when the installed provider exposes the supported public event contract. Current provider builds without that contract remain fail-closed and are reported by diagnostics. |''')
replace(
    "README.md",
    '''- [Live staging checklist](docs/STAGING.md)''',
    '''- [Live staging checklist](docs/STAGING.md)\n- [Integration contracts and metadata](docs/INTEGRATIONS.md)''')

write("docs/INTEGRATIONS.md", r'''# PlexonQuests 3.0 integrations

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
''')

# Focused regression tests that do not require optional providers to be installed in MockBukkit.
write("src/test/java/com/zpkdxgames/plexonquests/objective/matcher/IntegrationObjectiveFiltersTest.java", r'''package com.zpkdxgames.plexonquests.objective.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationObjectiveFiltersTest {
    @Test
    void rankMetadataMatchesNormalizedFilters() {
        assertTrue(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_RANK_UP,
                Map.of("to-rank", "Technician-1", "to-category", "TECHNICIAN"),
                Map.of("rank.to", "technician-1", "rank.category.to", "technician")));
    }

    @Test
    void missingMetadataFailsClosed() {
        assertFalse(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_KEY_EARN,
                Map.of("key-category", "epic"),
                Map.of()));
    }

    @Test
    void numericThresholdsAreEnforced() {
        assertTrue(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_TOOL_LEVEL_UP,
                Map.of("minimum-level", "50"),
                Map.of("tool.level.new", "51")));
        assertFalse(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_TOOL_LEVEL_UP,
                Map.of("minimum-level", "50"),
                Map.of("tool.level.new", "49")));
    }

    @Test
    void unsupportedIntegrationFilterIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IntegrationObjectiveFilters.validate(
                ObjectiveType.PLEXON_KEY_EARN, Map.of("to-rank", "technician")));
    }
}
''')

write("src/test/java/com/zpkdxgames/plexonquests/api/ExternalProgressContributionTest.java", r'''package com.zpkdxgames.plexonquests.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalProgressContributionTest {
    @Test
    void metadataIsNormalizedAndImmutable() {
        ExternalProgressContribution contribution = new ExternalProgressContribution(
                ExternalObjectiveType.PLEXON_RANK_UP,
                1L,
                true,
                Map.of("Rank.To", "Technician-1"),
                "rankup:test");
        assertEquals("Technician-1", contribution.metadata().get("rank.to"));
        assertThrows(UnsupportedOperationException.class, () -> contribution.metadata().put("x", "y"));
    }

    @Test
    void oldConstructorRemainsAvailable() {
        ExternalProgressContribution contribution = new ExternalProgressContribution(
                ExternalObjectiveType.PLEXON_CRATE_OPEN, 1L, true, "crateopen:test");
        assertEquals(Map.of(), contribution.metadata());
    }
}
''')

# The source tree must stay clean after transformation.
print("PlexonQuests 3.0 source transformation applied")
