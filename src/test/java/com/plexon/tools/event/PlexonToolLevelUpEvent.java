package com.plexon.tools.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlexonToolLevelUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String toolId;
    private final long oldLevel;
    private final long newLevel;
    private final String toolCategory;
    private final String eventId;

    public PlexonToolLevelUpEvent(
            Player player,
            String toolId,
            long oldLevel,
            long newLevel,
            String toolCategory,
            String eventId) {
        this.player = player;
        this.toolId = toolId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.toolCategory = toolCategory;
        this.eventId = eventId;
    }

    public Player getPlayer() {
        return player;
    }

    public String toolId() {
        return toolId;
    }

    public long oldLevel() {
        return oldLevel;
    }

    public long newLevel() {
        return newLevel;
    }

    public String toolCategory() {
        return toolCategory;
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
