package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MenuListener implements Listener {
    private static final EnumSet<ClickType> UNSAFE = EnumSet.of(
            ClickType.NUMBER_KEY,
            ClickType.SWAP_OFFHAND,
            ClickType.DROP,
            ClickType.CONTROL_DROP,
            ClickType.DOUBLE_CLICK,
            ClickType.MIDDLE,
            ClickType.CREATIVE);

    private final ConfigManager configs;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public MenuListener(ConfigManager configs) {
        this.configs = configs;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewerId().equals(player.getUniqueId())
                || UNSAFE.contains(event.getClick())
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        long now = System.nanoTime();
        long cooldown = configs.snapshot().settings().security().guiClickCooldownMillis() * 1_000_000L;
        long previous = lastClick.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
        if (previous != Long.MIN_VALUE && now - previous < cooldown) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        MenuAction action = holder.action(event.getRawSlot());
        if (action != null) {
            action.execute(player, event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QuestMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}

