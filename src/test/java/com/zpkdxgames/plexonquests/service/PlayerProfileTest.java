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

class PlayerProfileTest {
    @Test
    void claimedQuestStillOccupiesItsRotationSlot() {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(
                playerId,
                TestFixtures.quest("claimed", CompletionMode.ALL, 1L),
                "daily",
                "daily:current",
                Instant.now(),
                Instant.now().plusSeconds(3_600));
        assertTrue(assignment.forceComplete(Instant.now()));
        assertTrue(assignment.markClaiming());
        assertTrue(assignment.markClaimed(Instant.now()));
        PlayerProfile profile = new PlayerProfile(
                playerId,
                "Player",
                FeedbackPreferences.defaults(),
                null,
                1L,
                List.of(assignment));

        assertTrue(profile.visibleAssignments().isEmpty());
        assertEquals(1, profile.assignments(QuestScope.DAILY, "daily:current").size());
    }
}
