package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class QuestClaimUncertainEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID assignmentId;
    private final String questId;
    private final String transactionId;
    private final String detail;

    public QuestClaimUncertainEvent(
            Player player, UUID assignmentId, String questId, String transactionId, String detail) {
        super(player);
        this.assignmentId = assignmentId;
        this.questId = questId;
        this.transactionId = transactionId;
        this.detail = detail;
    }

    public UUID assignmentId() { return assignmentId; }

    public String questId() { return questId; }

    public String transactionId() { return transactionId; }

    public String detail() { return detail; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

