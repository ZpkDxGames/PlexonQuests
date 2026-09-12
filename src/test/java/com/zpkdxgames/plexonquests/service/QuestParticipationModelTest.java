package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestParticipationModelTest {
    @Test
    void completedQuestFreesRuntimeSlotButStillConsumesPeriodBudget() {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(playerId,
                TestFixtures.quest("daily-one", CompletionMode.ALL, 1L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        PlayerProfile profile = profile(playerId, List.of(assignment));
        assertEquals(1, profile.activeAssignments().size());
        assertEquals(1, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
        assertTrue(assignment.forceComplete(Instant.now()));
        assertTrue(profile.activeAssignments().isEmpty());
        assertEquals(1, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
    }

    @Test
    void cancelledQuestRemainsPartOfPeriodParticipationHistory() {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(playerId,
                TestFixtures.quest("daily-two", CompletionMode.ALL, 5L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        PlayerProfile profile = profile(playerId, List.of(assignment));
        assertTrue(assignment.cancel());
        assertTrue(profile.activeAssignments().isEmpty());
        assertEquals(1, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
    }

    @Test
    void legacyMultipleActiveAssignmentsArePreservedByProfileModel() {
        UUID playerId = UUID.randomUUID();
        QuestAssignment first = QuestAssignment.create(playerId,
                TestFixtures.quest("legacy-one", CompletionMode.ALL, 5L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        QuestAssignment second = QuestAssignment.create(playerId,
                TestFixtures.quest("legacy-two", CompletionMode.ALL, 5L), "daily", "daily:test",
                Instant.now(), Instant.now().plusSeconds(3600));
        PlayerProfile profile = profile(playerId, List.of(first, second));
        assertEquals(2, profile.activeAssignments().size());
        assertEquals(2, profile.periodParticipationCount(QuestScope.DAILY, "daily:test"));
    }

    private static PlayerProfile profile(UUID playerId, List<QuestAssignment> assignments) {
        return new PlayerProfile(playerId, "Player", FeedbackPreferences.defaults(), null, 0L, assignments);
    }
}
