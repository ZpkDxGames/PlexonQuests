package com.zpkdxgames.plexonquests.persistence;

import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.Instant;
import java.util.UUID;

public record HistoryEntry(
        long id,
        UUID assignmentId,
        String questId,
        String displayName,
        String rarity,
        QuestScope scope,
        AssignmentState state,
        Instant assignedAt,
        Instant completedAt,
        Instant claimedAt,
        String objectiveSummary,
        String rewardSummary) {}

