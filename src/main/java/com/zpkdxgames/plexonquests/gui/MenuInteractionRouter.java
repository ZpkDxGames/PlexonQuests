package com.zpkdxgames.plexonquests.gui;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared safety policy for custom inventory interactions.
 *
 * <p>The originating inventory event remains responsible for cancelling item movement and
 * validating its own holder/slot/action. This router owns the cross-menu semantic click policy
 * and defers actions that may open, replace, or close an inventory until after the click event
 * has returned to Paper.</p>
 */
final class MenuInteractionRouter {
    private MenuInteractionRouter() {
    }

    static boolean supportsAction(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT;
    }

    static void defer(
            JavaPlugin plugin,
            Player player,
            InventoryHolder expectedHolder,
            Runnable action) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(expectedHolder, "expectedHolder");
        Objects.requireNonNull(action, "action");

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()
                    || player.getOpenInventory().getTopInventory().getHolder() != expectedHolder) {
                return;
            }
            action.run();
        });
    }
}
