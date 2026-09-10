package com.zpkdxgames.plexonquests.api;

import com.zpkdxgames.plexonquests.quest.JournalState;
import java.util.Set;

/** Immutable 4.x quest-journal projection safe for external plugins to retain. */
public record JournalQuestView(
        String questId,
        String category,
        String scope,
        JournalState state,
        long current,
        long required,
        boolean tracked,
        Set<String> prerequisites,
        Set<String> missingPrerequisites) {

    public JournalQuestView {
        prerequisites = Set.copyOf(prerequisites);
        missingPrerequisites = Set.copyOf(missingPrerequisites);
    }
}
