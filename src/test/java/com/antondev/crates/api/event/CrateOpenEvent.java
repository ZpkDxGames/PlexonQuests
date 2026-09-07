package com.antondev.crates.api.event;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class CrateOpenEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final OpeningPlan plan;

    public CrateOpenEvent(Player player, OpeningPlan plan) {
        this.player = player;
        this.plan = plan;
    }

    public Player player() {
        return player;
    }

    public OpeningPlan plan() {
        return plan;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public record OpeningPlan(
            String transactionId,
            String crateId,
            String keyId,
            long openingCount,
            List<String> rewardIds,
            String source) {}
}
