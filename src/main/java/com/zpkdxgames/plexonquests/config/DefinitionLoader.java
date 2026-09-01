package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.objective.ObjectiveFilters;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import com.zpkdxgames.plexonquests.quest.ClaimMode;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.IconDefinition;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDisplay;
import com.zpkdxgames.plexonquests.quest.QuestEligibility;
import com.zpkdxgames.plexonquests.quest.QuestRegistrySnapshot;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.quest.RarityDefinition;
import com.zpkdxgames.plexonquests.reward.RewardBundle;
import com.zpkdxgames.plexonquests.reward.RewardDefinition;
import com.zpkdxgames.plexonquests.reward.RewardMode;
import com.zpkdxgames.plexonquests.reward.RewardType;
import com.zpkdxgames.plexonquests.util.DurationParser;
import com.zpkdxgames.plexonquests.util.Hashing;
import com.zpkdxgames.plexonquests.util.Identifiers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class DefinitionLoader {
    private final Path dataDirectory;
    private final List<ValidationIssue> issues = new ArrayList<>();

    public DefinitionLoader(Path dataDirectory) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }

    public QuestRegistrySnapshot load() {
        Map<String, QuestDefinition> quests = new LinkedHashMap<>();
        for (Path file : yamlFiles(dataDirectory.resolve("quests"))) {
            parseQuest(file).ifPresent(quest -> {
                QuestDefinition duplicate = quests.putIfAbsent(quest.id(), quest);
                if (duplicate != null) {
                    error(source(file) + ".id", "Duplicate quest ID also defined by " + duplicate.source());
                }
            });
        }

        Map<String, PoolDefinition> pools = new LinkedHashMap<>();
        for (Path file : yamlFiles(dataDirectory.resolve("pools"))) {
            parsePool(file).ifPresent(pool -> {
                PoolDefinition duplicate = pools.putIfAbsent(pool.id(), pool);
                if (duplicate != null) {
                    error(source(file) + ".id", "Duplicate pool ID also defined by " + duplicate.source());
                }
            });
        }

        pools.entrySet().removeIf(entry -> {
            PoolDefinition pool = entry.getValue();
            List<String> missing = pool.questWeights().keySet().stream().filter(id -> !quests.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                error(pool.source() + ".quests", "Unknown quest IDs: " + String.join(", ", missing));
                return true;
            }
            boolean wrongScope = pool.questWeights().keySet().stream()
                    .map(quests::get)
                    .anyMatch(quest -> quest.scope() != pool.scope());
            if (wrongScope) {
                error(pool.source() + ".quests", "Every pool quest must use the pool scope");
                return true;
            }
            return false;
        });

        Map<String, RarityDefinition> rarities = loadRarities(dataDirectory.resolve("rarities.yml"));
        quests.entrySet().removeIf(entry -> {
            if (!rarities.containsKey(entry.getValue().rarity())) {
                error(entry.getValue().source() + ".rarity", "Unknown rarity " + entry.getValue().rarity());
                return true;
            }
            return false;
        });

        return new QuestRegistrySnapshot(quests, pools, rarities, issues, Instant.now());
    }

    private java.util.Optional<QuestDefinition> parseQuest(Path file) {
        String location = source(file);
        try {
            YamlConfiguration yaml = read(file);
            requireSchema(yaml, location);
            String id = identifier(yaml.getString("id"), location + ".id");
            int revision = yaml.getInt("revision", 1);
            if (revision <= 0) {
                throw invalid(location + ".revision", "must be positive");
            }
            QuestScope scope = enumValue(QuestScope.class, yaml.getString("scope"), location + ".scope");
            QuestEligibility eligibility = parseEligibility(yaml.getConfigurationSection("eligibility"));
            QuestDisplay display = parseDisplay(requiredSection(yaml, "display", location));
            CompletionMode completion = enumValue(
                    CompletionMode.class, yaml.getString("completion-mode", "ALL"), location + ".completion-mode");
            ClaimMode claim = enumValue(ClaimMode.class, yaml.getString("claim-mode", "MANUAL"), location + ".claim-mode");
            Map<String, ObjectiveDefinition> objectives = parseObjectives(
                    requiredSection(yaml, "objectives", location), location + ".objectives");
            if (objectives.isEmpty()) {
                throw invalid(location + ".objectives", "at least one objective is required");
            }
            RewardBundle rewards = parseRewards(requiredSection(yaml, "rewards", location), location + ".rewards");
            ConfigurationSection effects = yaml.getConfigurationSection("effects");
            String completeEffect = effects == null ? "" : effects.getString("complete", "");
            String claimEffect = effects == null ? "quest-claim" : effects.getString("claim", "quest-claim");
            String raw = Files.readString(file);
            QuestDefinition definition = new QuestDefinition(
                    id,
                    revision,
                    yaml.getBoolean("enabled", true),
                    scope,
                    identifier(yaml.getString("category", "general"), location + ".category"),
                    yaml.getString("rarity", "COMMON").toUpperCase(Locale.ROOT),
                    Math.max(1, yaml.getInt("weight", 1)),
                    eligibility,
                    display,
                    completion,
                    claim,
                    objectives,
                    rewards,
                    completeEffect,
                    claimEffect,
                    Hashing.sha256(raw),
                    location);
            return java.util.Optional.of(definition);
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            error(location, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<PoolDefinition> parsePool(Path file) {
        String location = source(file);
        try {
            YamlConfiguration yaml = read(file);
            requireSchema(yaml, location);
            String id = identifier(yaml.getString("id"), location + ".id");
            QuestScope scope = enumValue(QuestScope.class, yaml.getString("scope"), location + ".scope");
            if (!scope.rotating()) {
                throw invalid(location + ".scope", "pools support DAILY or WEEKLY scopes only");
            }
            ConfigurationSection mix = yaml.getConfigurationSection("mix");
            ConfigurationSection entries = requiredSection(yaml, "quests", location);
            Map<String, Integer> weights = new LinkedHashMap<>();
            for (String questId : entries.getKeys(false)) {
                identifier(questId, location + ".quests." + questId);
                int weight = entries.getInt(questId);
                if (weight <= 0) {
                    throw invalid(location + ".quests." + questId, "weight must be positive");
                }
                weights.put(questId, weight);
            }
            Set<String> requiredIntegrations = upperSet(yaml.getStringList("eligibility.required-integrations"));
            PoolDefinition pool = new PoolDefinition(
                    id,
                    yaml.getBoolean("enabled", true),
                    scope,
                    Math.max(0, yaml.getInt("base-assignments", 0)),
                    yaml.getBoolean("prevent-duplicates", true),
                    DurationParser.parse(yaml.getString("recent-history-exclusion", "0d")),
                    mix == null ? List.of() : lowerList(mix.getStringList("guaranteed-categories")),
                    mix == null ? Integer.MAX_VALUE : mix.getInt("maximum-per-category", Integer.MAX_VALUE),
                    lowerSet(yaml.getStringList("eligibility.required-permissions")),
                    lowerSet(yaml.getStringList("eligibility.blocked-permissions")),
                    lowerSet(yaml.getStringList("eligibility.rank-categories")),
                    Set.copyOf(yaml.getStringList("eligibility.worlds")),
                    requiredIntegrations,
                    weights,
                    location);
            return java.util.Optional.of(pool);
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            error(location, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return java.util.Optional.empty();
        }
    }

    private Map<String, RarityDefinition> loadRarities(Path file) {
        Map<String, RarityDefinition> output = new LinkedHashMap<>();
        String location = source(file);
        try {
            YamlConfiguration yaml = read(file);
            requireSchema(yaml, location);
            ConfigurationSection section = requiredSection(yaml, "rarities", location);
            for (String rawId : section.getKeys(false)) {
                String id = rawId.toUpperCase(Locale.ROOT);
                ConfigurationSection rarity = requiredSection(section, rawId, location + ".rarities");
                Material icon = material(rarity.getString("icon", "PAPER"), location + ".rarities." + id + ".icon");
                output.put(id, new RarityDefinition(
                        id,
                        rarity.getString("display", id),
                        rarity.getString("color", "#D8DEE9"),
                        icon,
                        rarity.getBoolean("glow", false),
                        rarity.getInt("sort-priority", 0),
                        rarity.getString("complete-effect", "quest-complete-common")));
            }
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            error(location, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        }
        return output;
    }

    private QuestEligibility parseEligibility(ConfigurationSection section) {
        if (section == null) {
            return new QuestEligibility("", Set.of(), Set.of(), Set.of(), Set.of());
        }
        return new QuestEligibility(
                section.getString("required-permission", section.getString("permission", "")),
                lowerSet(section.getStringList("blocked-permissions")),
                lowerSet(section.getStringList("rank-categories")),
                Set.copyOf(section.getStringList("worlds")),
                upperSet(section.getStringList("required-integrations")));
    }

    private QuestDisplay parseDisplay(ConfigurationSection section) {
        ConfigurationSection icon = section.getConfigurationSection("icon");
        Material material = material(icon == null ? "PAPER" : icon.getString("material", "PAPER"), section.getCurrentPath() + ".icon.material");
        IconDefinition iconDefinition = new IconDefinition(
                material,
                icon == null ? 1 : icon.getInt("amount", 1),
                icon == null || icon.getBoolean("glow-when-complete", true),
                icon == null || !icon.contains("custom-model-data") ? null : icon.getInt("custom-model-data"),
                icon == null ? "" : icon.getString("texture", ""),
                icon == null ? "" : icon.getString("serialized-item", ""));
        return new QuestDisplay(
                iconDefinition,
                section.getString("name", "<white>Unnamed Quest"),
                section.getString("short-description", ""),
                section.getString("lore-template", "default-quest-card"));
    }

    private Map<String, ObjectiveDefinition> parseObjectives(ConfigurationSection section, String path) {
        Map<String, ObjectiveDefinition> output = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            identifier(id, path + "." + id);
            ConfigurationSection objective = requiredSection(section, id, path);
            ObjectiveType type = enumValue(ObjectiveType.class, objective.getString("type"), path + "." + id + ".type");
            long amount = objective.getLong("amount", 0L);
            if (amount <= 0L) {
                throw invalid(path + "." + id + ".amount", "must be positive");
            }
            ObjectiveFilters filters = parseFilters(objective.getConfigurationSection("filters"), path + "." + id + ".filters");
            output.put(id, new ObjectiveDefinition(id, type, amount, objective.getString("display", id), filters));
        }
        return output;
    }

    private ObjectiveFilters parseFilters(ConfigurationSection section, String path) {
        if (section == null) {
            return ObjectiveFilters.empty();
        }
        Set<Material> materials = enumSet(section.getStringList("materials"), this::matchMaterial, path + ".materials");
        Set<Material> caught = enumSet(section.getStringList("caught-materials"), this::matchMaterial, path + ".caught-materials");
        Set<EntityType> entities = enumSet(section.getStringList("entities"), value -> enumValue(EntityType.class, value, path), path + ".entities");
        Set<EntityDamageEvent.DamageCause> causes = enumSet(
                section.getStringList("damage-causes"),
                value -> enumValue(EntityDamageEvent.DamageCause.class, value, path),
                path + ".damage-causes");
        Set<CreatureSpawnEvent.SpawnReason> reasons = enumSet(
                section.getStringList("spawn-reasons"),
                value -> enumValue(CreatureSpawnEvent.SpawnReason.class, value, path),
                path + ".spawn-reasons");
        Set<GameMode> modes = enumSet(
                section.getStringList("game-modes"), value -> enumValue(GameMode.class, value, path), path + ".game-modes");
        Set<World.Environment> environments = enumSet(
                section.getStringList("world-environments"),
                value -> enumValue(World.Environment.class, value, path),
                path + ".world-environments");
        OriginPolicy origin = enumValue(
                OriginPolicy.class, section.getString("origin", "ANY"), path + ".origin");
        long maximum = section.getLong("maximum-contribution", Long.MAX_VALUE);
        long cooldown = 0L;
        if (section.contains("cooldown")) {
            cooldown = DurationParser.parse(section.getString("cooldown", "0ms")).toMillis();
        }
        Map<String, String> extras = new LinkedHashMap<>();
        Set<String> known = Set.of(
                "materials", "caught-materials", "entities", "damage-causes", "spawn-reasons", "game-modes",
                "worlds", "world-environments", "movement-types", "advancements", "required-permissions",
                "blocked-permissions", "origin", "mature-only", "hostile-only", "unique-only", "exclude-teleports",
                "minimum-contribution", "maximum-contribution", "cooldown");
        for (String key : section.getKeys(false)) {
            if (!known.contains(key) && !section.isConfigurationSection(key)) {
                extras.put(key, String.valueOf(section.get(key)));
            }
        }
        return new ObjectiveFilters(
                materials,
                caught,
                entities,
                causes,
                reasons,
                modes,
                Set.copyOf(section.getStringList("worlds")),
                environments,
                upperSet(section.getStringList("movement-types")),
                lowerSet(section.getStringList("advancements")),
                lowerSet(section.getStringList("required-permissions")),
                lowerSet(section.getStringList("blocked-permissions")),
                origin,
                section.getBoolean("mature-only", false),
                section.getBoolean("hostile-only", false),
                section.getBoolean("unique-only", false),
                section.getBoolean("exclude-teleports", true),
                Math.max(0L, section.getLong("minimum-contribution", 0L)),
                maximum,
                cooldown,
                extras);
    }

    private RewardBundle parseRewards(ConfigurationSection section, String path) {
        RewardMode mode = enumValue(RewardMode.class, section.getString("mode", "ALL"), path + ".mode");
        ConfigurationSection entries = requiredSection(section, "entries", path);
        List<RewardDefinition> output = new ArrayList<>();
        for (String id : entries.getKeys(false)) {
            identifier(id, path + ".entries." + id);
            ConfigurationSection reward = requiredSection(entries, id, path + ".entries");
            RewardType type = enumValue(RewardType.class, reward.getString("type"), path + ".entries." + id + ".type");
            Material material = null;
            if (reward.contains("material")) {
                material = material(reward.getString("material"), path + ".entries." + id + ".material");
            }
            String durationText = reward.getString("duration", "0ms");
            Duration duration = DurationParser.parse(durationText);
            long amount = Math.max(0L, reward.getLong("amount", 0L));
            double decimal = Math.max(0D, reward.getDouble("amount", 0D));
            RewardDefinition definition = new RewardDefinition(
                    id,
                    type,
                    amount,
                    decimal,
                    material,
                    reward.getString("command", ""),
                    reward.getString("permission", ""),
                    duration,
                    reward.getString("category", ""),
                    reward.getString("fallback-command", ""),
                    reward.getString("serialized-item", ""),
                    reward.getString("display", id),
                    Math.max(1, reward.getInt("weight", 1)));
            validateReward(definition, path + ".entries." + id);
            output.add(definition);
        }
        if (output.isEmpty()) {
            throw invalid(path + ".entries", "at least one reward is required");
        }
        return new RewardBundle(mode, output);
    }

    private void validateReward(RewardDefinition reward, String path) {
        switch (reward.type()) {
            case ITEM -> {
                if (reward.material() == null && reward.serializedItem().isBlank()) {
                    throw invalid(path, "ITEM requires material or serialized-item");
                }
                if (reward.amount() <= 0L) {
                    throw invalid(path + ".amount", "must be positive");
                }
            }
            case EXPERIENCE_POINTS, EXPERIENCE_LEVELS -> {
                if (reward.amount() <= 0L) {
                    throw invalid(path + ".amount", "must be positive");
                }
            }
            case MONEY -> {
                if (reward.decimalAmount() <= 0D) {
                    throw invalid(path + ".amount", "must be positive");
                }
            }
            case COMMAND, PLAYER_COMMAND -> {
                if (reward.command().isBlank()) {
                    throw invalid(path + ".command", "is required");
                }
                if (reward.command().contains("\n") || reward.command().contains("\r")) {
                    throw invalid(path + ".command", "must be a single line");
                }
            }
            case PERMISSION -> {
                if (reward.permission().isBlank()) {
                    throw invalid(path + ".permission", "is required");
                }
            }
            case PLEXON_KEY -> {
                if (reward.keyCategory().isBlank()) {
                    throw invalid(path + ".category", "is required");
                }
            }
            case MESSAGE, SOUND, EFFECT -> {
                // Cosmetic entries use their display/command fields at delivery time.
            }
        }
    }

    private YamlConfiguration read(Path file) throws IOException, InvalidConfigurationException {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(dataDirectory)) {
            throw new IllegalArgumentException("Configuration path escapes the plugin directory");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(normalized));
        return yaml;
    }

    private void requireSchema(YamlConfiguration yaml, String location) {
        int schema = yaml.getInt("schema-version", -1);
        if (schema != 1) {
            throw invalid(location + ".schema-version", "expected 1 but found " + schema);
        }
    }

    private List<Path> yamlFiles(Path root) {
        if (!Files.isDirectory(root)) {
            error(source(root), "Directory is missing");
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            error(source(root), "Unable to list definitions: " + exception.getMessage());
            return List.of();
        }
    }

    private String source(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(dataDirectory)
                ? dataDirectory.relativize(normalized).toString().replace('\\', '/')
                : normalized.getFileName().toString();
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String key, String path) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw invalid(path + "." + key, "section is required");
        }
        return section;
    }

    private static String identifier(String value, String path) {
        if (!Identifiers.valid(value)) {
            throw invalid(path, "must be a lowercase identifier using letters, numbers, underscores, or hyphens");
        }
        return value;
    }

    private Material material(String value, String path) {
        Material result = matchMaterial(value);
        if (result == null || result.isAir()) {
            throw invalid(path, "unknown material " + value);
        }
        return result;
    }

    private Material matchMaterial(String value) {
        return value == null ? null : Material.matchMaterial(value.toUpperCase(Locale.ROOT));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String path) {
        if (value == null) {
            throw invalid(path, "value is required");
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(path, "unknown " + type.getSimpleName() + " value " + value);
        }
    }

    private static <T> Set<T> enumSet(List<String> values, Function<String, T> resolver, String path) {
        Set<T> output = new LinkedHashSet<>();
        for (String value : values) {
            T parsed = resolver.apply(value);
            if (parsed == null) {
                throw invalid(path, "unknown value " + value);
            }
            output.add(parsed);
        }
        return Set.copyOf(output);
    }

    private static Set<String> lowerSet(List<String> values) {
        return Set.copyOf(lowerList(values));
    }

    private static List<String> lowerList(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    private static Set<String> upperSet(List<String> values) {
        return values.stream().map(value -> value.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }

    private void error(String path, String message) {
        issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, path, message));
    }
}

