package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class QuestPreClaimEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID assignmentId;
    private final String questId;
    private boolean cancelled;

    public QuestPreClaimEvent(Player player, UUID assignmentId, String questId) {
        super(player);
        this.assignmentId = assignmentId;
        this.questId = questId;
    }

    public UUID assignmentId() { return assignmentId; }

    public String questId() { return questId; }

    @Override public boolean isCancelled() { return cancelled; }

    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

