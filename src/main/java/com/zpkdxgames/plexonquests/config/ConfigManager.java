package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.util.AtomicFiles;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestRegistrySnapshot;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {
    private static final int CURRENT_MENU_LAYOUT = 3;
    private static final int CURRENT_POOL_CATALOG = 2;
    private static final Map<String, Integer> LEGACY_DAILY_QUESTS = Map.of(
            "stonebound", 12,
            "timber-trail", 12,
            "anglers-call", 8,
            "harvest-moon", 10,
            "monster-patrol", 9,
            "artisan-hours", 10,
            "world-wanderer", 8,
            "steady-hand", 9);
    private static final Map<String, Integer> LEGACY_WEEKLY_QUESTS = Map.of(
            "deep-delver", 12,
            "master-angler", 9,
            "realm-walker", 8,
            "hunters-ledger", 10,
            "community-merchant", 6,
            "tool-forged", 6);
    private static final List<String> DEFAULT_RESOURCES = List.of(
            "config.yml",
            "messages.yml",
            "menus.yml",
            "effects.yml",
            "rarities.yml",
            "pools/daily.yml",
            "pools/weekly.yml",
            "quests/daily/stonebound.yml",
            "quests/daily/timber-trail.yml",
            "quests/daily/anglers-call.yml",
            "quests/daily/harvest-moon.yml",
            "quests/daily/monster-patrol.yml",
            "quests/daily/artisan-hours.yml",
            "quests/daily/world-wanderer.yml",
            "quests/daily/steady-hand.yml",
            "quests/daily/adventurers-shift.yml",
            "quests/daily/arcane-practice.yml",
            "quests/daily/builders-blueprint.yml",
            "quests/daily/nether-forager.yml",
            "quests/daily/ore-prospector.yml",
            "quests/daily/redstone-workshop.yml",
            "quests/daily/smelters-shift.yml",
            "quests/weekly/deep-delver.yml",
            "quests/weekly/master-angler.yml",
            "quests/weekly/realm-walker.yml",
            "quests/weekly/hunters-ledger.yml",
            "quests/weekly/community-merchant.yml",
            "quests/weekly/tool-forged.yml",
            "quests/weekly/arcane-arsenal.yml",
            "quests/weekly/harvest-festival.yml",
            "quests/weekly/industrial-output.yml",
            "quests/weekly/makers-mark.yml",
            "quests/weekly/nether-quarry.yml",
            "quests/weekly/week-in-motion.yml",
            "quests/milestones/first-steps.yml",
            "quests/milestones/quest-apprentice.yml",
            "quests/milestones/quest-veteran.yml",
            "quests/milestones/quest-champion.yml",
            "quests/milestones/quest-legend.yml",
            "quests/milestones/first-rankup.yml");

    private final JavaPlugin plugin;
    private final Path dataDirectory;
    private final AtomicReference<ConfigSnapshot> active = new AtomicReference<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
    }

    public ConfigSnapshot loadInitial() throws IOException, InvalidConfigurationException {
        installDefaults();
        Candidate candidate = loadCandidate();
        if (!candidate.activationErrors().isEmpty()) {
            throw new InvalidConfigurationException(String.join("; ", candidate.activationErrors()));
        }
        active.set(candidate.snapshot());
        return candidate.snapshot();
    }

    public CompletableFuture<ReloadResult> reloadAsync(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Candidate candidate = loadCandidate();
                if (!candidate.activationErrors().isEmpty()) {
                    return new ReloadResult(false, active.get(), candidate.snapshot().registry().issues(), candidate.activationErrors());
                }
                active.set(candidate.snapshot());
                return new ReloadResult(true, candidate.snapshot(), candidate.snapshot().registry().issues(), List.of());
            } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
                return new ReloadResult(
                        false,
                        active.get(),
                        active.get() == null ? List.of() : active.get().registry().issues(),
                        List.of(Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName())));
            }
        }, executor);
    }

    public ConfigSnapshot snapshot() {
        ConfigSnapshot snapshot = active.get();
        if (snapshot == null) {
            throw new IllegalStateException("Configuration has not been loaded");
        }
        return snapshot;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    private Candidate loadCandidate() throws IOException, InvalidConfigurationException {
        YamlConfiguration root = loadYaml("config.yml");
        requireSchema(root, "config.yml");
        PluginSettings settings = PluginSettings.parse(root);
        YamlConfiguration messages = loadYaml("messages.yml");
        YamlConfiguration menus = loadYaml("menus.yml");
        YamlConfiguration effects = loadYaml("effects.yml");
        requireSchema(messages, "messages.yml");
        requireSchema(menus, "menus.yml");
        requireSchema(effects, "effects.yml");

        List<String> activationErrors = new ArrayList<>();
        validateMenus(menus, activationErrors);
        QuestRegistrySnapshot registry = new DefinitionLoader(
                        dataDirectory, settings.rotation().recentHistoryExclusion())
                .load();
        registry = withPoolCapacityWarnings(registry, settings);
        if (registry.quests().isEmpty()) {
            activationErrors.add("No valid quests were loaded");
        }
        if (registry.pools().values().stream().noneMatch(pool -> pool.enabled() && pool.scope().rotating())) {
            activationErrors.add("No valid rotating quest pool was loaded");
        }
        if (registry.rarities().isEmpty()) {
            activationErrors.add("No valid rarities were loaded");
        }

        ConfigSnapshot snapshot = new ConfigSnapshot(
                settings,
                registry,
                FlatConfiguration.from(messages),
                FlatConfiguration.from(menus),
                FlatConfiguration.from(effects),
                Instant.now());
        return new Candidate(snapshot, List.copyOf(activationErrors));
    }

    private void installDefaults() throws IOException {
        Files.createDirectories(dataDirectory);
        Files.createDirectories(dataDirectory.resolve("backups"));
        Files.createDirectories(dataDirectory.resolve("logs"));
        for (String resource : DEFAULT_RESOURCES) {
            Path target = dataDirectory.resolve(resource).normalize();
            if (!target.startsWith(dataDirectory)) {
                throw new IOException("Bundled resource path escaped the data directory");
            }
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                plugin.saveResource(resource, false);
            }
        }
        migrateDefaultPool("pools/daily.yml", "DAILY", 3, "7d", List.of("gathering"), 2, LEGACY_DAILY_QUESTS);
        migrateDefaultPool("pools/weekly.yml", "WEEKLY", 2, "21d", List.of(), 1, LEGACY_WEEKLY_QUESTS);
        migrateMenus();
    }

    private void migrateDefaultPool(
            String resource,
            String scope,
            int baseAssignments,
            String historyExclusion,
            List<String> guaranteedCategories,
            int maximumPerCategory,
            Map<String, Integer> legacyQuests) throws IOException {
        Path path = dataDirectory.resolve(resource).normalize();
        String existing = Files.readString(path, StandardCharsets.UTF_8);
        YamlConfiguration pool = new YamlConfiguration();
        try {
            pool.loadFromString(existing);
        } catch (InvalidConfigurationException exception) {
            return;
        }
        int catalog = pool.getInt("catalog-version", 1);
        String expectedId = Path.of(resource).getFileName().toString().replace(".yml", "");
        if (catalog >= CURRENT_POOL_CATALOG || !isLegacyDefaultPool(
                pool,
                expectedId,
                scope,
                baseAssignments,
                historyExclusion,
                guaranteedCategories,
                maximumPerCategory,
                legacyQuests)) {
            return;
        }

        Path backup = dataDirectory.resolve("backups")
                .resolve(expectedId + "-catalog-v" + catalog + "-" + Instant.now().toEpochMilli() + ".yml");
        Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
        try (InputStream bundled = plugin.getResource(resource)) {
            if (bundled == null) {
                throw new IOException("Bundled " + resource + " is missing");
            }
            AtomicFiles.writeUtf8(path, new String(bundled.readAllBytes(), StandardCharsets.UTF_8));
        }
        plugin.getLogger().info("Upgraded " + resource + " to catalog version " + CURRENT_POOL_CATALOG
                + "; previous pool saved as " + backup.getFileName());
    }

    private static boolean isLegacyDefaultPool(
            YamlConfiguration pool,
            String id,
            String scope,
            int baseAssignments,
            String historyExclusion,
            List<String> guaranteedCategories,
            int maximumPerCategory,
            Map<String, Integer> legacyQuests) {
        ConfigurationSection quests = pool.getConfigurationSection("quests");
        if (quests == null) {
            return false;
        }
        Map<String, Integer> weights = new LinkedHashMap<>();
        quests.getKeys(false).forEach(questId -> weights.put(questId, quests.getInt(questId)));
        ConfigurationSection mix = pool.getConfigurationSection("mix");
        return pool.getKeys(false).equals(Set.of(
                        "schema-version",
                        "id",
                        "enabled",
                        "scope",
                        "base-assignments",
                        "prevent-duplicates",
                        "recent-history-exclusion",
                        "mix",
                        "quests"))
                && mix != null
                && mix.getKeys(false).equals(Set.of("guaranteed-categories", "maximum-per-category"))
                && pool.getInt("schema-version", -1) == 1
                && id.equals(pool.getString("id", ""))
                && pool.getBoolean("enabled", false)
                && scope.equalsIgnoreCase(pool.getString("scope", ""))
                && pool.getInt("base-assignments", -1) == baseAssignments
                && pool.getBoolean("prevent-duplicates", false)
                && historyExclusion.equalsIgnoreCase(pool.getString("recent-history-exclusion", ""))
                && guaranteedCategories.equals(pool.getStringList("mix.guaranteed-categories"))
                && pool.getInt("mix.maximum-per-category", -1) == maximumPerCategory
                && legacyQuests.equals(weights);
    }

    private void migrateMenus() throws IOException {
        Path menusPath = dataDirectory.resolve("menus.yml");
        String existing = Files.readString(menusPath, StandardCharsets.UTF_8);
        YamlConfiguration menus = new YamlConfiguration();
        try {
            menus.loadFromString(existing);
        } catch (InvalidConfigurationException exception) {
            return;
        }
        int layout = menus.getInt("layout-version", 1);
        if (layout >= CURRENT_MENU_LAYOUT) {
            return;
        }

        Path backup = dataDirectory.resolve("backups")
                .resolve("menus-v" + layout + "-" + Instant.now().toEpochMilli() + ".yml");
        Files.copy(menusPath, backup, StandardCopyOption.COPY_ATTRIBUTES);
        try (InputStream bundled = plugin.getResource("menus.yml")) {
            if (bundled == null) {
                throw new IOException("Bundled menus.yml is missing");
            }
            AtomicFiles.writeUtf8(menusPath, new String(bundled.readAllBytes(), StandardCharsets.UTF_8));
        }
        plugin.getLogger().info("Upgraded menus.yml to layout version " + CURRENT_MENU_LAYOUT
                + "; previous layout saved as " + backup.getFileName());
    }

    private YamlConfiguration loadYaml(String relative) throws IOException, InvalidConfigurationException {
        Path path = dataDirectory.resolve(relative).normalize();
        if (!path.startsWith(dataDirectory)) {
            throw new IOException("Configuration path escaped the data directory");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(path));
        return yaml;
    }

    private static void requireSchema(YamlConfiguration yaml, String file) throws InvalidConfigurationException {
        int schema = yaml.getInt("schema-version", -1);
        if (schema != 1) {
            throw new InvalidConfigurationException(file + ".schema-version must be 1");
        }
    }

    private static void validateMenus(YamlConfiguration yaml, List<String> errors) {
        if (yaml.getInt("layout-version", -1) != CURRENT_MENU_LAYOUT) {
            errors.add("menus.yml.layout-version must be " + CURRENT_MENU_LAYOUT);
        }
        validateMenu(yaml, "journal", "quest-slots", errors);
        validateMenu(yaml, "details", "objective-slots", errors);
        validateMenu(yaml, "history", "entry-slots", errors);
        validateMenu(yaml, "admin", "quest-slots", errors);
        validateMenu(yaml, "settings", null, errors);
        validateMenu(yaml, "confirmation", null, errors);
    }

    private static QuestRegistrySnapshot withPoolCapacityWarnings(
            QuestRegistrySnapshot registry, PluginSettings settings) {
        List<ValidationIssue> issues = new ArrayList<>(registry.issues());
        for (PoolDefinition pool : registry.pools().values()) {
            int target = pool.scope() == QuestScope.DAILY
                    ? settings.assignments().maximumDailySlots()
                    : settings.assignments().maximumWeeklySlots();
            List<QuestDefinition> enabled = pool.questWeights().keySet().stream()
                    .map(registry.quests()::get)
                    .filter(Objects::nonNull)
                    .filter(QuestDefinition::enabled)
                    .toList();
            int capacity = constrainedCapacity(enabled, pool);
            if (capacity < target) {
                issues.add(new ValidationIssue(
                        ValidationIssue.Severity.WARNING,
                        pool.source() + ".quests",
                        "Pool can supply at most " + capacity + " unique quest(s), below the configured "
                                + target + "-slot maximum"));
            }
            List<QuestDefinition> unconditional = enabled.stream()
                    .filter(ConfigManager::unconditionallyEligible)
                    .toList();
            int unconditionalCapacity = constrainedCapacity(unconditional, pool);
            if (unconditionalCapacity < target) {
                issues.add(new ValidationIssue(
                        ValidationIssue.Severity.WARNING,
                        pool.source() + ".eligibility",
                        "Only " + unconditionalCapacity + " slot(s) are guaranteed without optional permissions, "
                                + "world restrictions, ranks, or integrations; configured maximum is " + target));
            }
        }
        return new QuestRegistrySnapshot(
                registry.quests(), registry.pools(), registry.rarities(), issues, registry.loadedAt());
    }

    private static int constrainedCapacity(List<QuestDefinition> quests, PoolDefinition pool) {
        int categoryCapacity = quests.stream()
                .collect(java.util.stream.Collectors.groupingBy(QuestDefinition::category, java.util.stream.Collectors.counting()))
                .values().stream()
                .mapToInt(count -> (int) Math.min(count, pool.maximumPerCategory()))
                .sum();
        int rarityCapacity = quests.stream()
                .collect(java.util.stream.Collectors.groupingBy(QuestDefinition::rarity, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .mapToInt(entry -> (int) Math.min(
                        entry.getValue(), pool.maximumPerRarity().getOrDefault(entry.getKey(), Integer.MAX_VALUE)))
                .sum();
        return Math.min(quests.size(), Math.min(categoryCapacity, rarityCapacity));
    }

    private static boolean unconditionallyEligible(QuestDefinition quest) {
        return quest.eligibility().requiredPermission().isBlank()
                && quest.eligibility().blockedPermissions().isEmpty()
                && quest.eligibility().rankCategories().isEmpty()
                && quest.eligibility().worlds().isEmpty()
                && quest.eligibility().requiredIntegrations().isEmpty();
    }

    private static void validateMenu(YamlConfiguration yaml, String path, String primarySlots, List<String> errors) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            errors.add("menus.yml." + path + " is missing");
            return;
        }
        int size = yaml.getInt(path + ".size", 0);
        if (size < 9 || size > 54 || size % 9 != 0) {
            errors.add("menus.yml." + path + ".size must be a multiple of 9 between 9 and 54");
            return;
        }
        if (primarySlots != null) {
            List<Integer> slots = section.getIntegerList(primarySlots);
            if (slots.isEmpty()) {
                errors.add("menus.yml." + path + "." + primarySlots + " cannot be empty");
            }
        }

        Map<Integer, String> occupied = new HashMap<>();
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (isSingleSlotKey(key) || isMappedSlotKey(key, value)) {
                if (value instanceof Number number) {
                    validateSlot(path, key, number.intValue(), size, occupied, errors);
                } else if (!(value instanceof ConfigurationSection)) {
                    errors.add("menus.yml." + path + "." + key + " must be an integer slot");
                }
            } else if (key.endsWith("-slots") && value instanceof List<?> slots) {
                for (int index = 0; index < slots.size(); index++) {
                    Object rawSlot = slots.get(index);
                    if (!(rawSlot instanceof Number number)) {
                        errors.add("menus.yml." + path + "." + key + " contains a non-integer slot");
                        continue;
                    }
                    validateSlot(path, key + "[" + index + "]", number.intValue(), size, occupied, errors);
                }
            }
        }
    }

    private static boolean isSingleSlotKey(String key) {
        return key.endsWith("-slot") || key.endsWith(".slot");
    }

    private static boolean isMappedSlotKey(String key, Object value) {
        int separator = key.lastIndexOf('.');
        return separator > 0
                && key.substring(0, separator).endsWith("-slots")
                && !(value instanceof ConfigurationSection);
    }

    private static void validateSlot(
            String menu,
            String key,
            int slot,
            int size,
            Map<Integer, String> occupied,
            List<String> errors) {
        String fullPath = "menus.yml." + menu + "." + key;
        if (slot < 0 || slot >= size) {
            errors.add(fullPath + " is out of bounds");
            return;
        }
        String previous = occupied.putIfAbsent(slot, fullPath);
        if (previous != null) {
            errors.add(fullPath + " overlaps " + previous + " at slot " + slot);
        }
    }

    private record Candidate(ConfigSnapshot snapshot, List<String> activationErrors) {}

    public record ReloadResult(
            boolean success,
            ConfigSnapshot snapshot,
            List<ValidationIssue> definitionIssues,
            List<String> activationErrors) {
        public ReloadResult {
            definitionIssues = List.copyOf(definitionIssues);
            activationErrors = List.copyOf(activationErrors);
        }
    }
}
