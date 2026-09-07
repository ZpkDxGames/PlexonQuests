package com.zpkdxgames.plexondailyrewards.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class DailyRewardClaimedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String tierId;
    private final int day;
    private final boolean forced;

    public DailyRewardClaimedEvent(Player player, String tierId, int day, boolean forced) {
        this.player = player;
        this.tierId = tierId;
        this.day = day;
        this.forced = forced;
    }

    public Player getPlayer() {
        return player;
    }

    public String getTierId() {
        return tierId;
    }

    public int getDay() {
        return day;
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
