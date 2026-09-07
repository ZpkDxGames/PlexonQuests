package com.zpkdxgames.plexonranks.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class PlexonRankupEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Rank from;
    private final Rank to;
    private final String transactionId;

    public PlexonRankupEvent(Player player, Rank from, Rank to, String transactionId) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.transactionId = transactionId;
    }

    public Player getPlayer() {
        return player;
    }

    public Rank from() {
        return from;
    }

    public Rank to() {
        return to;
    }

    public String transactionId() {
        return transactionId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public record Rank(String id) {}
}
