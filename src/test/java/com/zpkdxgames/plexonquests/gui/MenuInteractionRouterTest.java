package com.zpkdxgames.plexonquests.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class MenuInteractionRouterTest {
    private static final Path LEGACY_LISTENER = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/gui/MenuListener.java");
    private static final Path JOURNAL = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java");
    private static final Path ROUTER = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/gui/MenuInteractionRouter.java");
    private static final Path PLUGIN = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java");

    @Test
    void semanticActionsAcceptOnlyOrdinaryLeftAndRightClicks() {
        assertTrue(MenuInteractionRouter.supportsAction(ClickType.LEFT));
        assertTrue(MenuInteractionRouter.supportsAction(ClickType.RIGHT));

        EnumSet.allOf(ClickType.class).stream()
                .filter(click -> click != ClickType.LEFT && click != ClickType.RIGHT)
                .forEach(click -> assertFalse(
                        MenuInteractionRouter.supportsAction(click),
                        () -> "Unsupported inventory click dispatched a semantic action: " + click));
    }

    @Test
    void bothGuiFamiliesRouteActionsThroughDeferredSafetyPolicy() throws IOException {
        String legacy = Files.readString(LEGACY_LISTENER);
        String journal = Files.readString(JOURNAL);

        assertTrue(legacy.contains("MenuInteractionRouter.supportsAction(event.getClick())"));
        assertTrue(legacy.contains("MenuInteractionRouter.defer(plugin, player, holder"));
        assertFalse(legacy.contains("action.execute(player, event.getClick())"));

        assertTrue(journal.contains("MenuInteractionRouter.supportsAction(event.getClick())"));
        assertTrue(journal.contains("MenuInteractionRouter.defer(plugin, player, holder"));
        assertFalse(journal.contains("action.run(player, event.getClick())"));
    }

    @Test
    void deferredActionRequiresSameOpenHolderAndOnlinePlayer() throws IOException {
        String source = Files.readString(ROUTER);
        assertTrue(source.contains("!player.isOnline()"));
        assertTrue(source.contains("getHolder() != expectedHolder"));
        assertTrue(source.contains("Bukkit.getScheduler().runTask(plugin"));
    }

    @Test
    void legacyListenerReceivesOwningPluginExplicitly() throws IOException {
        String listener = Files.readString(LEGACY_LISTENER);
        String plugin = Files.readString(PLUGIN);
        assertTrue(listener.contains("MenuListener(JavaPlugin plugin, ConfigManager configs)"));
        assertTrue(plugin.contains("new MenuListener(this, configs)"));
    }
}
