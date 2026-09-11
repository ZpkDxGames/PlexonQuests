package com.zpkdxgames.plexonquests.gui.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase3JournalUxArchitectureTest {
    private static final Path JOURNAL = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java");

    @Test
    void playerJournalNoLongerDelegatesIntoLegacyMenuFamily() throws IOException {
        String source = Files.readString(JOURNAL);
        assertFalse(source.contains("legacy.open"));
        assertFalse(source.contains("MenuContext.journal"));
        assertTrue(source.contains("JournalNavigationContext"));
    }

    @Test
    void playerJournalDoesNotExposeEngineeringTerminology() throws IOException {
        String source = Files.readString(JOURNAL);
        assertFalse(source.contains("Definition Health"));
        assertFalse(source.contains("SQLite"));
        assertFalse(source.contains("Prerequisite graph"));
        assertFalse(source.contains("Accept Quest"));
        assertFalse(source.contains("Pin Quest"));
        assertFalse(source.contains("STale_REVISION"));
    }

    @Test
    void stateChangingActionsStillUseExistingAuthorities() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("rewards.claim(player, live)"));
        assertTrue(source.contains("tracking.toggle(player, assignmentId)"));
        assertTrue(source.contains("rerolls.prepare(player, live"));
        assertTrue(source.contains("rerolls.confirm(p)"));
        assertTrue(source.contains("storage.history(player.getUniqueId()"));
        assertFalse(source.contains("new AssignmentService"));
        assertFalse(source.contains("new ProgressService"));
    }

    @Test
    void journalAddsNoRepeatingGuiTask() throws IOException {
        String source = Files.readString(JOURNAL);
        assertFalse(source.contains("runTaskTimer"));
        assertFalse(source.contains("runTaskTimerAsynchronously"));
    }

    @Test
    void assignmentMechanismsRemainTruthfulAndNoManualAcceptIsInvented() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("May be assigned by the daily rotation."));
        assertTrue(source.contains("May be assigned by the weekly rotation."));
        assertTrue(source.contains("Starts automatically when its requirements are met."));
        assertTrue(source.contains("Assigned by server staff."));
        assertFalse(source.contains("Accept Quest"));
    }
}
