package com.zpkdxgames.plexonquests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.api.PlexonQuestsAPI;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.gui.QuestItemRenderer;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
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
    void layoutOneMenusAreBackedUpAndMigrated() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        Path menus = plugin.getDataFolder().toPath().resolve("menus.yml");
        String layoutOne = Files.readString(menus).replace("layout-version: 2\n", "");
        Files.writeString(menus, layoutOne);

        ConfigManager configs = new ConfigManager(plugin);
        configs.loadInitial();

        assertTrue(Files.readString(menus).contains("layout-version: 2"));
        try (var backups = Files.list(plugin.getDataFolder().toPath().resolve("backups"))) {
            assertTrue(backups.anyMatch(path -> path.getFileName().toString().startsWith("menus-v1-")));
        }
    }
}
