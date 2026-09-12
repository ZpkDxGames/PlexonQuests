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
    private static final Path PARTICIPATION = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/service/QuestParticipationService.java");
    private static final Path ROTATION = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/rotation/RotationService.java");
    private static final Path COMMAND = Path.of(
            "src/main/java/com/zpkdxgames/plexonquests/command/Phase2QuestCommand.java");

    @Test
    void playerJournalIsJoinBasedAndDoesNotBecomeAssignmentAuthority() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("participation.join(player, offer)"));
        assertTrue(source.contains("participation.abandon(p, assignment.id())"));
        assertTrue(source.contains("rewards.claim(player, live)"));
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
    void catalogAndHistoryRenderingValidateExactOpenHolder() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("player.getOpenInventory().getTopInventory().getHolder() != holder"));
        assertTrue(source.contains("storage.history(player.getUniqueId(), pageSize + 1"));
        assertTrue(source.contains("Bukkit.getScheduler().runTask(plugin"));
    }

    @Test
    void joinAndAbandonHaveDuplicateSubmissionGuards() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("holder.submit(\"join\")"));
        assertTrue(source.contains("holder.submit(\"abandon\")"));
        assertTrue(source.contains("holder.submit(\"claim\")"));
        assertTrue(source.contains("CLICK_DEBOUNCE_NANOS"));
    }

    @Test
    void exactHolderDeferredClickContractRemainsInUse() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("MenuInteractionRouter.supportsAction(event.getClick())"));
        assertTrue(source.contains("MenuInteractionRouter.defer(plugin, player, holder"));
        assertTrue(source.contains("onDrag(InventoryDragEvent event)"));
    }

    @Test
    void realPlayerHeadAndSkillsSurfaceArePresent() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("items.playerHead(player"));
        assertTrue(source.contains("openStatistics(Player player)"));
        assertTrue(source.contains("Skill profile loading..."));
        assertTrue(source.contains("Skills unavailable"));
    }

    @Test
    void currentAvailableCompletedAndLegacyOverflowStatesAreExplicit() throws IOException {
        String source = Files.readString(JOURNAL);
        assertTrue(source.contains("No quest joined"));
        assertTrue(source.contains("Available Quests"));
        assertTrue(source.contains("No completed quests yet"));
        assertTrue(source.contains("Legacy active quests"));
        assertTrue(source.contains("Join Quest"));
        assertTrue(source.contains("Abandon Quest"));
    }

    @Test
    void rotationNoLongerAutoCreatesNormalAssignments() throws IOException {
        String source = Files.readString(ROTATION);
        assertFalse(source.contains("assignments.add(player, profile"));
        assertTrue(source.contains("profile.expirePast"));
        assertTrue(source.contains("progress.reindex(profile)"));
    }

    @Test
    void participationOwnsLiveValidationAndAsyncInsert() throws IOException {
        String source = Files.readString(PARTICIPATION);
        assertTrue(source.contains("inFlight.add(player.getUniqueId())"));
        assertTrue(source.contains("storage.questIdsForPeriod"));
        assertTrue(source.contains("assignments.add(player, liveProfile"));
        assertTrue(source.contains("profile.activeAssignments().size()"));
        assertTrue(source.contains("progress.reindex(liveProfile)"));
    }

    @Test
    void rerollCommandExplainsBoardInsteadOfMutatingAssignments() throws IOException {
        String source = Files.readString(COMMAND);
        assertTrue(source.contains("Rerolls are no longer needed"));
        assertTrue(source.contains("journal.openAvailable(player, 0, null)"));
        assertFalse(source.contains("journal.openReroll(player, assignment)"));
    }

    @Test
    void playerFacingJournalDoesNotExposeRawQuestIds() throws IOException {
        String source = Files.readString(JOURNAL);
        assertFalse(source.contains("Quest ID"));
        assertTrue(source.contains("questName(questId)"));
    }
}
