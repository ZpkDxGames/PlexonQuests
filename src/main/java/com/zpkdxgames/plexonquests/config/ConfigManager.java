package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.util.AtomicFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {
    private static final int CURRENT_MENU_LAYOUT = 2;
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
            "quests/weekly/deep-delver.yml",
            "quests/weekly/master-angler.yml",
            "quests/weekly/realm-walker.yml",
            "quests/weekly/hunters-ledger.yml",
            "quests/weekly/community-merchant.yml",
            "quests/weekly/tool-forged.yml",
            "quests/milestones/first-steps.yml",
            "quests/milestones/quest-veteran.yml",
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
        var registry = new DefinitionLoader(dataDirectory).load();
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
        migrateMenus();
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
