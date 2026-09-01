package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class QuestClaimedEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID assignmentId;
    private final String questId;
    private final String transactionId;

    public QuestClaimedEvent(Player player, UUID assignmentId, String questId, String transactionId) {
        super(player);
        this.assignmentId = assignmentId;
        this.questId = questId;
        this.transactionId = transactionId;
    }

    public UUID assignmentId() { return assignmentId; }

    public String questId() { return questId; }

    public String transactionId() { return transactionId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

