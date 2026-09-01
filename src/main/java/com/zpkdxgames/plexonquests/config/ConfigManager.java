package com.zpkdxgames.plexonquests.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {
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
        validateMenu(yaml, "journal", "quest-slots", errors);
        validateMenu(yaml, "details", "objective-slots", errors);
        validateMenu(yaml, "history", "entry-slots", errors);
        validateMenu(yaml, "admin", "quest-slots", errors);
        validateMenu(yaml, "settings", null, errors);
        validateMenu(yaml, "confirmation", null, errors);
    }

    private static void validateMenu(YamlConfiguration yaml, String path, String primarySlots, List<String> errors) {
        int size = yaml.getInt(path + ".size", 0);
        if (size < 9 || size > 54 || size % 9 != 0) {
            errors.add("menus.yml." + path + ".size must be a multiple of 9 between 9 and 54");
            return;
        }
        if (primarySlots != null) {
            List<Integer> slots = yaml.getIntegerList(path + "." + primarySlots);
            if (slots.isEmpty()) {
                errors.add("menus.yml." + path + "." + primarySlots + " cannot be empty");
            }
            Set<Integer> seen = new HashSet<>();
            for (int slot : slots) {
                if (slot < 0 || slot >= size) {
                    errors.add("menus.yml." + path + "." + primarySlots + " contains out-of-bounds slot " + slot);
                } else if (!seen.add(slot)) {
                    errors.add("menus.yml." + path + "." + primarySlots + " contains duplicate slot " + slot);
                }
            }
        }
        for (String key : yaml.getConfigurationSection(path).getKeys(false)) {
            if (!key.endsWith("-slot")) {
                continue;
            }
            int slot = yaml.getInt(path + "." + key, -1);
            if (slot < 0 || slot >= size) {
                errors.add("menus.yml." + path + "." + key + " is out of bounds");
            }
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

