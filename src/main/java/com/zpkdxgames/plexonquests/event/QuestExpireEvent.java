package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired synchronously after an expired assignment is queued for persistence. */
public final class QuestExpireEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID assignmentId;
    private final String questId;

    public QuestExpireEvent(Player player, UUID assignmentId, String questId) {
        super(player);
        this.assignmentId = assignmentId;
        this.questId = questId;
    }

    public UUID assignmentId() { return assignmentId; }

    public String questId() { return questId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
