package com.zpkdxgames.plexonquests.persistence;

import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.service.FeedbackPreferences;
import java.util.List;
import java.util.UUID;

public record StoredProfile(
        UUID playerId,
        String latestName,
        FeedbackPreferences preferences,
        UUID pinnedAssignment,
        long completedTotal,
        List<QuestAssignment> assignments) {
    public StoredProfile {
        assignments = List.copyOf(assignments);
    }
}

