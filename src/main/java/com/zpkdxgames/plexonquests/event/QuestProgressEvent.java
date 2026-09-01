package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class QuestProgressEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID assignmentId;
    private final String questId;
    private final String objectiveId;
    private long acceptedDelta;
    private boolean cancelled;

    public QuestProgressEvent(
            Player player, UUID assignmentId, String questId, String objectiveId, long acceptedDelta) {
        super(player);
        this.assignmentId = assignmentId;
        this.questId = questId;
        this.objectiveId = objectiveId;
        this.acceptedDelta = acceptedDelta;
    }

    public UUID assignmentId() {
        return assignmentId;
    }

    public String questId() {
        return questId;
    }

    public String objectiveId() {
        return objectiveId;
    }

    public long acceptedDelta() {
        return acceptedDelta;
    }

    public void acceptedDelta(long acceptedDelta) {
        if (acceptedDelta < 0L) {
            throw new IllegalArgumentException("Accepted quest progress cannot be negative");
        }
        this.acceptedDelta = acceptedDelta;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}

