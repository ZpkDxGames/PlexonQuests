package com.zpkdxgames.plexonquests.event;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired after the 4.x tracked/pinned assignment changes. */
public final class QuestTrackEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID assignmentId;
    private final String questId;
    private final boolean tracked;

    public QuestTrackEvent(Player player, UUID assignmentId, String questId, boolean tracked) {
        super(player);
        this.assignmentId = assignmentId;
        this.questId = questId;
        this.tracked = tracked;
    }

    public UUID assignmentId() { return assignmentId; }

    public String questId() { return questId; }

    public boolean tracked() { return tracked; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
