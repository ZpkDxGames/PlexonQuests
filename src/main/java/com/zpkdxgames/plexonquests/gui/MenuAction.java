package com.zpkdxgames.plexonquests.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

@FunctionalInterface
public interface MenuAction {
    void execute(Player player, ClickType click);
}

