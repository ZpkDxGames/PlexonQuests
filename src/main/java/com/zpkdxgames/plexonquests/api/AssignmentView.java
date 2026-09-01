package com.zpkdxgames.plexonquests.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable assignment snapshot. No mutable Bukkit or internal model is exposed. */
public record AssignmentView(
        UUID assignmentId,
        String questId,
        String scope,
        String state,
        String displayName,
        String rarity,
        String periodKey,
        Instant assignedAt,
        Instant expiresAt,
        double percentage,
        List<ObjectiveView> objectives) {
    public AssignmentView {
        objectives = List.copyOf(objectives);
    }

    public boolean complete() {
        return state.equals("COMPLETED") || state.equals("CLAIMING") || state.equals("CLAIMED");
    }

    public boolean claimable() {
        return state.equals("COMPLETED");
    }
}
