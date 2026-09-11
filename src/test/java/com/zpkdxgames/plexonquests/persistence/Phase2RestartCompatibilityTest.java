package com.zpkdxgames.plexonquests.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.config.PluginSettings;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.service.FeedbackPreferences;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase2RestartCompatibilityTest {
    @TempDir
    Path directory;

    private StorageService storage;

    @AfterEach
    void close() {
        if (storage != null) storage.close();
    }

    @Test
    void activeProgressTrackingCompletionHistoryAndClaimIdentitySurviveRepeatedStartup() throws Exception {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        QuestAssignment active = QuestAssignment.create(
                playerId,
                TestFixtures.quest("legacy-active", CompletionMode.ALL, 10),
                "daily",
                "daily:compat",
                now.minusSeconds(60),
                now.plus(Duration.ofHours(3)));
        QuestAssignment claimed = QuestAssignment.create(
                playerId,
                TestFixtures.quest("legacy-claimed", CompletionMode.ALL, 1),
                "daily",
                "daily:compat",
                now.minusSeconds(60),
                now.plus(Duration.ofHours(3)));

        storage = open();
        assertTrue(storage.insertAssignment(active, "CompatTester").get(5, TimeUnit.SECONDS));
        active.addProgress("objective_1", 4, now);
        storage.markDirty(active);
        storage.flushDirty().get(5, TimeUnit.SECONDS);
        storage.savePreferences(playerId, "CompatTester", FeedbackPreferences.defaults(), active.id()).get(5, TimeUnit.SECONDS);

        claimed.forceComplete(now);
        assertTrue(storage.insertAssignment(claimed, "CompatTester").get(5, TimeUnit.SECONDS));
        assertTrue(claimed.markClaiming());
        assertTrue(storage.reserveClaim("compat-claim", claimed).get(5, TimeUnit.SECONDS));
        storage.completeClaim("compat-claim", claimed).get(5, TimeUnit.SECONDS);
        assertTrue(claimed.markClaimed(now));

        storage.close();
        storage = open();
        assertCompatible(storage.loadProfile(playerId, "CompatTester").get(5, TimeUnit.SECONDS), active.id());
        assertTrue(storage.history(playerId, 100, 0).get(5, TimeUnit.SECONDS).stream()
                .anyMatch(entry -> entry.questId().equals("legacy-claimed") && entry.state() == AssignmentState.CLAIMED));

        storage.close();
        storage = open();
        assertCompatible(storage.loadProfile(playerId, "CompatTester").get(5, TimeUnit.SECONDS), active.id());
    }

    private static void assertCompatible(StoredProfile profile, UUID trackedId) {
        assertEquals(trackedId, profile.pinnedAssignment());
        QuestAssignment active = profile.assignments().stream()
                .filter(assignment -> assignment.definition().id().equals("legacy-active"))
                .findFirst().orElseThrow();
        assertEquals(4L, active.objective("objective_1").orElseThrow().current());
        QuestAssignment claimed = profile.assignments().stream()
                .filter(assignment -> assignment.definition().id().equals("legacy-claimed"))
                .findFirst().orElseThrow();
        assertEquals(AssignmentState.CLAIMED, claimed.state());
        assertTrue(profile.completedTotal() >= 1L);
    }

    private StorageService open() throws Exception {
        StorageService created = new StorageService(
                directory,
                new PluginSettings.Storage(
                        "compat.db",
                        5_000,
                        Duration.ofMillis(10),
                        Duration.ofMinutes(5),
                        128,
                        Duration.ofSeconds(5),
                        180,
                        500),
                Logger.getLogger(Phase2RestartCompatibilityTest.class.getName()));
        created.start();
        return created;
    }
}
