package com.zpkdxgames.plexonquests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.api.PlexonQuestsAPI;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.gui.QuestItemRenderer;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.rotation.WeightedSelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PlexonQuestsPluginTest {
    private ServerMock server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    @Test
    void bundledConfigurationStartsAndRegistersPublicApi() {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);

        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("quests"));
        assertNotNull(Bukkit.getServicesManager().load(PlexonQuestsAPI.class));
    }

    @Test
    void bundledCatalogCanFillEveryConfiguredRotationSlotWithoutIntegrations() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        ConfigManager configs = new ConfigManager(plugin);
        var snapshot = configs.loadInitial();
        var daily = snapshot.registry().pool("daily").orElseThrow();
        var weekly = snapshot.registry().pool("weekly").orElseThrow();

        assertEquals(33, snapshot.registry().quests().size());
        assertEquals(15, daily.questWeights().size());
        assertEquals(12, weekly.questWeights().size());
        assertTrue(daily.questWeights().size() >= snapshot.settings().assignments().maximumDailySlots());
        long coreWeekly = weekly.questWeights().keySet().stream()
                .map(snapshot.registry().quests()::get)
                .filter(quest -> quest.eligibility().requiredIntegrations().isEmpty())
                .count();
        assertTrue(coreWeekly >= snapshot.settings().assignments().maximumWeeklySlots());
        WeightedSelector selector = new WeightedSelector();
        assertEquals(
                snapshot.settings().assignments().maximumDailySlots(),
                selector.select(
                                daily,
                                snapshot.registry().quests(),
                                snapshot.settings().assignments().maximumDailySlots(),
                                7L,
                                Set.of(),
                                quest -> quest.eligibility().requiredIntegrations().isEmpty())
                        .size());
        assertEquals(
                snapshot.settings().assignments().maximumWeeklySlots(),
                selector.select(
                                weekly,
                                snapshot.registry().quests(),
                                snapshot.settings().assignments().maximumWeeklySlots(),
                                11L,
                                Set.of(),
                                quest -> quest.eligibility().requiredIntegrations().isEmpty())
                        .size());
        assertEquals(0L, snapshot.registry().errorCount());
    }

    @Test
    void configuredLoreRendersComponentsAndHidesVanillaAttributes() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        ConfigManager configs = new ConfigManager(plugin);
        configs.loadInitial();
        TextService text = new TextService(configs);
        QuestItemRenderer renderer = new QuestItemRenderer(configs, text, new ItemFactory());

        ItemStack item = renderer.configured(
                "journal.pinned",
                text.placeholders(
                        "pinned_name", "Timber Trail",
                        "percentage", "50",
                        "progress_color", "#FFD166"),
                Map.of("progress_bar", Component.text("VISIBLE-BAR")));
        String lore = Objects.requireNonNull(item.getItemMeta().lore()).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", String::concat);

        assertTrue(lore.contains("VISIBLE-BAR"));
        assertFalse(lore.contains("<progress_bar>"));
        assertTrue(item.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));
    }

    @Test
    void earlierMenusAreBackedUpAndMigrated() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        Path menus = plugin.getDataFolder().toPath().resolve("menus.yml");
        String layoutOne = Files.readString(menus).replace("layout-version: 3\n", "");
        Files.writeString(menus, layoutOne);

        ConfigManager configs = new ConfigManager(plugin);
        configs.loadInitial();

        assertTrue(Files.readString(menus).contains("layout-version: 3"));
        try (var backups = Files.list(plugin.getDataFolder().toPath().resolve("backups"))) {
            assertTrue(backups.anyMatch(path -> path.getFileName().toString().startsWith("menus-v1-")));
        }
    }

    @Test
    void untouchedLegacyDailyPoolIsBackedUpAndExpanded() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        Path pool = plugin.getDataFolder().toPath().resolve("pools/daily.yml");
        Files.writeString(pool, """
                schema-version: 1
                id: daily
                enabled: true
                scope: DAILY
                base-assignments: 3
                prevent-duplicates: true
                recent-history-exclusion: 7d
                mix:
                  guaranteed-categories: [gathering]
                  maximum-per-category: 2
                quests:
                  stonebound: 12
                  timber-trail: 12
                  anglers-call: 8
                  harvest-moon: 10
                  monster-patrol: 9
                  artisan-hours: 10
                  world-wanderer: 8
                  steady-hand: 9
                """);

        ConfigManager configs = new ConfigManager(plugin);
        configs.loadInitial();

        String migrated = Files.readString(pool);
        assertTrue(migrated.contains("catalog-version: 2"));
        assertTrue(migrated.contains("adventurers-shift: 12"));
        try (var backups = Files.list(plugin.getDataFolder().toPath().resolve("backups"))) {
            assertTrue(backups.anyMatch(path -> path.getFileName().toString().startsWith("daily-catalog-v1-")));
        }
    }

    @Test
    void customizedLegacyPoolIsNotOverwritten() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        Path pool = plugin.getDataFolder().toPath().resolve("pools/daily.yml");
        Files.writeString(pool, """
                schema-version: 1
                id: daily
                enabled: true
                scope: DAILY
                base-assignments: 3
                prevent-duplicates: true
                recent-history-exclusion: 7d
                eligibility:
                  excluded-worlds: [creative]
                mix:
                  guaranteed-categories: [gathering]
                  maximum-per-category: 2
                quests:
                  stonebound: 12
                  timber-trail: 12
                  anglers-call: 8
                  harvest-moon: 10
                  monster-patrol: 9
                  artisan-hours: 10
                  world-wanderer: 8
                  steady-hand: 9
                """);

        ConfigManager configs = new ConfigManager(plugin);
        configs.loadInitial();

        String preserved = Files.readString(pool);
        assertFalse(preserved.contains("catalog-version:"));
        assertTrue(preserved.contains("excluded-worlds: [creative]"));
    }
}
