package com.plexon.tools.event;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlexonToolProgressEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String toolId;
    private final long amount;
    private final long level;
    private final String toolCategory;
    private final String progressType;
    private final Material material;
    private final String eventId;

    public PlexonToolProgressEvent(
            Player player,
            String toolId,
            long amount,
            long level,
            String toolCategory,
            String progressType,
            Material material,
            String eventId) {
        this.player = player;
        this.toolId = toolId;
        this.amount = amount;
        this.level = level;
        this.toolCategory = toolCategory;
        this.progressType = progressType;
        this.material = material;
        this.eventId = eventId;
    }

    public Player getPlayer() {
        return player;
    }

    public String toolId() {
        return toolId;
    }

    public long amount() {
        return amount;
    }

    public long level() {
        return level;
    }

    public String toolCategory() {
        return toolCategory;
    }

    public String progressType() {
        return progressType;
    }

    public Material material() {
        return material;
    }

    public String eventId() {
        return eventId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
