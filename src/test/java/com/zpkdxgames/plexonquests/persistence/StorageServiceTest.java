package com.zpkdxgames.plexonquests.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.config.PluginSettings;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {
    @TempDir
    Path directory;

    private StorageService storage;

    @AfterEach
    void closeStorage() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void persistsProgressAndContributionTokensAcrossRestart() throws Exception {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(
                playerId,
                TestFixtures.quest("stored-progress", CompletionMode.ALL, 10L),
                "daily",
                "daily:2026-09-01",
                Instant.parse("2026-09-01T04:00:00Z"),
                Instant.parse("2026-09-02T04:00:00Z"));
        storage = open();

        assertTrue(storage.insertAssignment(assignment, "StorageTester").get(5, TimeUnit.SECONDS));
        assertTrue(assignment.addProgress("objective_1", 4L, Instant.now()).accepted());
        storage.markDirty(assignment);
        assertEquals(1, storage.flushDirty().get(5, TimeUnit.SECONDS));
        assertTrue(storage.reserveContributionToken(playerId, "SHOP_VISIT", "purchase-42")
                .get(5, TimeUnit.SECONDS));
        assertFalse(storage.reserveContributionToken(playerId, "SHOP_VISIT", "purchase-42")
                .get(5, TimeUnit.SECONDS));

        storage.close();
        storage = open();
        StoredProfile loaded = storage.loadProfile(playerId, "StorageTester").get(5, TimeUnit.SECONDS);
        assertEquals(1, loaded.assignments().size());
        assertEquals(4L, loaded.assignments().getFirst().objective("objective_1").orElseThrow().current());
        assertFalse(storage.reserveContributionToken(playerId, "SHOP_VISIT", "purchase-42")
                .get(5, TimeUnit.SECONDS));
    }

    @Test
    void interruptedReservedClaimBecomesUncertainOnRestart() throws Exception {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(
                playerId,
                TestFixtures.quest("claim-recovery", CompletionMode.ALL, 1L),
                "daily",
                "daily:2026-09-01",
                Instant.parse("2026-09-01T04:00:00Z"),
                Instant.parse("2026-09-02T04:00:00Z"));
        assertTrue(assignment.forceComplete(Instant.parse("2026-09-01T05:00:00Z")));
        storage = open();
        assertTrue(storage.insertAssignment(assignment, "ClaimTester").get(5, TimeUnit.SECONDS));
        assertTrue(assignment.markClaiming());
        assertTrue(storage.reserveClaim("claim-transaction", assignment).get(5, TimeUnit.SECONDS));

        storage.close();
        storage = open();
        StoredProfile loaded = storage.loadProfile(playerId, "ClaimTester").get(5, TimeUnit.SECONDS);

        assertEquals(1L, storage.diagnostics().uncertainClaims());
        assertEquals(AssignmentState.CLAIMING, loaded.assignments().getFirst().state());
    }

    @Test
    void claimedRotatingAssignmentsRemainLoadedUntilTheirPeriodEnds() throws Exception {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        QuestAssignment current = QuestAssignment.create(
                playerId,
                TestFixtures.quest("claimed-current", CompletionMode.ALL, 1L),
                "daily",
                "daily:current",
                now.minusSeconds(60),
                now.plus(Duration.ofHours(2)));
        storage = open();
        assertTrue(current.forceComplete(now));
        assertTrue(storage.insertAssignment(current, "ClaimedTester").get(5, TimeUnit.SECONDS));
        assertTrue(current.markClaiming());
        assertTrue(storage.reserveClaim("current-claim", current).get(5, TimeUnit.SECONDS));
        storage.completeClaim("current-claim", current).get(5, TimeUnit.SECONDS);
        assertTrue(current.markClaimed(now));

        storage.close();
        storage = open();
        StoredProfile loaded = storage.loadProfile(playerId, "ClaimedTester").get(5, TimeUnit.SECONDS);

        assertEquals(1, loaded.assignments().size());
        assertEquals(AssignmentState.CLAIMED, loaded.assignments().getFirst().state());
    }

    @Test
    void periodQuestIdsIncludeCancelledAssignmentsThatCannotBeReinserted() throws Exception {
        UUID playerId = UUID.randomUUID();
        QuestAssignment assignment = QuestAssignment.create(
                playerId,
                TestFixtures.quest("already-used", CompletionMode.ALL, 1L),
                "daily",
                "daily:current",
                Instant.now(),
                Instant.now().plus(Duration.ofHours(2)));
        storage = open();
        assertTrue(storage.insertAssignment(assignment, "PeriodTester").get(5, TimeUnit.SECONDS));
        assertTrue(assignment.cancel());
        storage.archive(assignment, AssignmentState.CANCELLED).get(5, TimeUnit.SECONDS);

        assertEquals(
                java.util.Set.of("already-used"),
                storage.questIdsForPeriod(playerId, "daily:current").get(5, TimeUnit.SECONDS));
    }

    private StorageService open() throws Exception {
        StorageService created = new StorageService(
                directory,
                new PluginSettings.Storage(
                        "test.db",
                        5_000,
                        Duration.ofMillis(10),
                        Duration.ofMinutes(5),
                        128,
                        Duration.ofSeconds(5),
                        180,
                        500),
                Logger.getLogger(StorageServiceTest.class.getName()));
        created.start();
        return created;
    }
}
