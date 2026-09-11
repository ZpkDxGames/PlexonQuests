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
    private static final Path COMMAND = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/command/Phase2QuestCommand.java");

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
        assertFalse(source.contains("STALE_REVISION"));
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

    @Test
    void emptyStatesAreActionableAndUsePlayerLanguage() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("No active quests"));
        assertTrue(source.contains("No eligible quests"));
        assertTrue(source.contains("No tracked quest"));
        assertTrue(source.contains("No completed quests yet"));
    }

    @Test
    void sharedControlBarUsesFinalPlexonSlotGeometry() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("item(holder, 45, Material.ARROW"));
        assertTrue(source.contains("item(holder, 48, Material.ARROW"));
        assertTrue(source.contains("item(holder, 49, Material.MAP"));
        assertTrue(source.contains("item(holder, 50, Material.ARROW"));
        assertTrue(source.contains("item(holder, 53, Material.EMERALD"));
        assertTrue(source.contains("openContext(p, parent)"));
    }

    @Test
    void sectionIdentityUsesReservedHeaderSlot() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("item(holder, 4, sectionMaterial(selected)"));
        assertTrue(source.contains("sectionLabel(selected)"));
    }

    @Test
    void staleAndDuplicateActionProtectionArePresent() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("This quest changed while the menu was open"));
        assertTrue(source.contains("holder.submit(\"claim\")"));
        assertTrue(source.contains("holder.submit(submission)"));
        assertTrue(source.contains("holder.release(submission)"));
        assertTrue(source.contains("holder.submit(\"prepare-reroll\")"));
        assertTrue(source.contains("holder.submit(\"reroll\")"));
    }

    @Test
    void rapidClicksAreDebouncedAndTrackingRefreshesInPlace() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("CLICK_DEBOUNCE_NANOS"));
        assertTrue(source.contains("holder.acceptInteraction()"));
        assertTrue(source.contains("refreshTrackingPresentation(player, holder, live, parent)"));

        int start = source.indexOf("private void toggleTracked(");
        int end = source.indexOf("private void runNextAction", start);
        assertTrue(start >= 0 && end > start);
        String toggle = source.substring(start, end);
        assertFalse(toggle.contains("openDetailsById"));
    }

    @Test
    void asyncHistoryValidatesExactOpenHolderBeforeRendering() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("storage.history(player.getUniqueId(), pageSize + 1"));
        assertTrue(source.contains("player.getOpenInventory().getTopInventory().getHolder() != holder"));
        assertTrue(source.contains("Bukkit.getScheduler().runTask(plugin"));
    }

    @Test
    void prerequisiteIdsResolveToPlayerFacingNames() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("questName(first)"));
        assertTrue(source.contains("questName(questId)"));
        assertFalse(source.contains("Quest ID"));
    }

    @Test
    void directRerollCommandUsesUnifiedJournalInsteadOfLegacyMenu() throws IOException {
        String source = Files.readString(COMMAND);
        assertTrue(source.contains("journal.openReroll(player, assignment)"));
        assertFalse(source.contains("menus.openReroll"));
        assertFalse(source.contains("MenuContext.journal"));
    }
}
