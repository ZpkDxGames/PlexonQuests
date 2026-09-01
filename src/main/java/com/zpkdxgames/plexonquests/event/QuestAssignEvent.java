package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired synchronously before a quest assignment is persisted. */
public final class QuestAssignEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID assignmentId;
    private final String questId;
    private final String poolId;
    private final String periodKey;
    private boolean cancelled;

    public QuestAssignEvent(Player player, UUID assignmentId, String questId, String poolId, String periodKey) {
        super(player);
        this.assignmentId = assignmentId;
        this.questId = questId;
        this.poolId = poolId;
        this.periodKey = periodKey;
    }

    public UUID assignmentId() { return assignmentId; }

    public String questId() { return questId; }

    public String poolId() { return poolId; }

    public String periodKey() { return periodKey; }

    @Override public boolean isCancelled() { return cancelled; }

    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
