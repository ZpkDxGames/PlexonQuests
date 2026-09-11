package com.zpkdxgames.plexonquests.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;
import org.junit.jupiter.api.Test;

class QuestStatePresentationTest {
    @Test
    void translatesTechnicalJournalStates() {
        assertEquals("ELIGIBLE", QuestStatePresentation.label(JournalState.AVAILABLE));
        assertEquals("READY TO CLAIM", QuestStatePresentation.label(JournalState.COMPLETABLE));
        assertEquals("ACTIVE", QuestStatePresentation.label(JournalState.TRACKED));
        assertEquals("UNAVAILABLE", QuestStatePresentation.label(JournalState.DISABLED));
    }

    @Test
    void trackingRemainsAnAttributeOfActive() {
        assertEquals("ACTIVE", QuestStatePresentation.label(AssignmentState.ACTIVE));
        assertEquals("TRACKED", QuestStatePresentation.tracking(true));
        assertEquals("NOT TRACKED", QuestStatePresentation.tracking(false));
    }

    @Test
    void completedAssignmentMeansReadyUntilClaimed() {
        assertEquals("READY TO CLAIM", QuestStatePresentation.label(AssignmentState.COMPLETED));
        assertEquals("COMPLETED", QuestStatePresentation.label(AssignmentState.CLAIMED));
    }
}
