package com.zpkdxgames.plexonquests.api;

import java.util.List;

/** Immutable definition metadata suitable for menus and integrations. */
public record QuestDefinitionView(
        String id,
        int revision,
        boolean enabled,
        String scope,
        String category,
        String rarity,
        String displayName,
        String description,
        String completionMode,
        String claimMode,
        List<String> objectiveIds) {
    public QuestDefinitionView {
        objectiveIds = List.copyOf(objectiveIds);
    }
}
