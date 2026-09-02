package com.zpkdxgames.plexonquests.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.TestFixtures;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestAssignmentTest {
    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void allModeRequiresEveryObjective() {
        QuestAssignment assignment = assignment(CompletionMode.ALL, 2L, 3L);

        ProgressResult first = assignment.addProgress("objective_1", 2L, START.plusSeconds(1));
        assertTrue(first.objectiveCompleted());
        assertFalse(first.questCompleted());
        assertEquals(AssignmentState.ACTIVE, assignment.state());

        ProgressResult second = assignment.addProgress("objective_2", 99L, START.plusSeconds(2));
        assertTrue(second.questCompleted());
        assertEquals(3L, second.newValue());
        assertEquals(AssignmentState.COMPLETED, assignment.state());
        assertEquals(100D, assignment.percentage());
    }

    @Test
    void anyModeCompletesOnOneObjective() {
        QuestAssignment assignment = assignment(CompletionMode.ANY, 2L, 10L);

        assertTrue(assignment.addProgress("objective_2", 10L, START.plusSeconds(1)).questCompleted());
        assertEquals(AssignmentState.COMPLETED, assignment.state());
        assertEquals(100D, assignment.percentage());
    }

    @Test
    void sequenceRejectsObjectivesOutOfOrder() {
        QuestAssignment assignment = assignment(CompletionMode.SEQUENCE, 1L, 1L);

        assertFalse(assignment.addProgress("objective_2", 1L, START.plusSeconds(1)).accepted());
        assertTrue(assignment.addProgress("objective_1", 1L, START.plusSeconds(2)).accepted());
        assertTrue(assignment.addProgress("objective_2", 1L, START.plusSeconds(3)).questCompleted());
    }

    @Test
    void claimReservationCanRollbackWithoutDuplicatingCompletion() {
        QuestAssignment assignment = assignment(CompletionMode.ALL, 1L);
        assertTrue(assignment.forceComplete(START.plusSeconds(1)));
        assertTrue(assignment.markClaiming());
        assertTrue(assignment.rollbackClaim());
        assertEquals(AssignmentState.COMPLETED, assignment.state());
        assertTrue(assignment.markClaiming());
        assertTrue(assignment.markClaimed(START.plusSeconds(2)));
        assertTrue(assignment.state().terminal());
        assertFalse(assignment.cancel());
        assertFalse(assignment.expire());
    }

    private static QuestAssignment assignment(CompletionMode mode, long... amounts) {
        return new QuestAssignment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TestFixtures.quest("assignment-test", mode, amounts),
                "daily",
                "daily:2026-09-01",
                START,
                START.plusSeconds(86_400),
                AssignmentState.ACTIVE,
                null,
                null,
                Map.of());
    }
}
