package com.zpkdxgames.plexonquests.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.TestFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletionIdempotencyTest {
    @Test
    void duplicateFinalObjectiveCannotCompleteAssignmentTwice() {
        QuestAssignment assignment = QuestAssignment.create(UUID.randomUUID(),
                TestFixtures.quest("duplicate", CompletionMode.ALL, 1), "daily", "period", Instant.now(), null);

        ProgressResult first = assignment.addProgress("objective_1", 1, Instant.now());
        ProgressResult duplicate = assignment.addProgress("objective_1", 1, Instant.now());

        assertTrue(first.questCompleted());
        assertFalse(duplicate.accepted());
        assertFalse(duplicate.questCompleted());
    }

    @Test
    void forceCompleteAndClaimTransitionsAreOneShot() {
        QuestAssignment assignment = QuestAssignment.create(UUID.randomUUID(),
                TestFixtures.quest("force", CompletionMode.ALL, 2), "daily", "period", Instant.now(), null);
        assertTrue(assignment.forceComplete(Instant.now()));
        assertFalse(assignment.forceComplete(Instant.now()));
        assertTrue(assignment.markClaiming());
        assertFalse(assignment.markClaiming());
        assertTrue(assignment.markClaimed(Instant.now()));
        assertFalse(assignment.markClaimed(Instant.now()));
    }
}
