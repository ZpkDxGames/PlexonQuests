package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public final class QuestRerollEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID oldAssignmentId;
    private final UUID newAssignmentId;
    private final String oldQuestId;
    private final String newQuestId;
    private final String transactionId;

    public QuestRerollEvent(
            Player player,
            UUID oldAssignmentId,
            UUID newAssignmentId,
            String oldQuestId,
            String newQuestId,
            String transactionId) {
        super(player);
        this.oldAssignmentId = oldAssignmentId;
        this.newAssignmentId = newAssignmentId;
        this.oldQuestId = oldQuestId;
        this.newQuestId = newQuestId;
        this.transactionId = transactionId;
    }

    public UUID oldAssignmentId() { return oldAssignmentId; }

    public UUID newAssignmentId() { return newAssignmentId; }

    public String oldQuestId() { return oldQuestId; }

    public String newQuestId() { return newQuestId; }

    public String transactionId() { return transactionId; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

