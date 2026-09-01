package com.zpkdxgames.plexonquests.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class QuestMenuHolder implements InventoryHolder {
    private final UUID viewerId;
    private final UUID sessionId = UUID.randomUUID();
    private final MenuContext context;
    private final Map<Integer, MenuAction> actions = new HashMap<>();
    private Inventory inventory;

    public QuestMenuHolder(UUID viewerId, MenuContext context) {
        this.viewerId = viewerId;
        this.context = context;
    }

    public void create(int size, net.kyori.adventure.text.Component title) {
        if (inventory != null) {
            throw new IllegalStateException("Menu inventory was already created");
        }
        inventory = Bukkit.createInventory(this, size, title);
    }

    public void action(int slot, MenuAction action) {
        actions.put(slot, action);
    }

    public MenuAction action(int slot) {
        return actions.get(slot);
    }

    public UUID viewerId() {
        return viewerId;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public MenuContext context() {
        return context;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Menu inventory is not initialized");
        }
        return inventory;
    }
}

