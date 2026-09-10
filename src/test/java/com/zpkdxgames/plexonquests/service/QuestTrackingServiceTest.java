package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.ConfigSnapshot;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestRegistrySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class QuestTrackingServiceTest {
    private Player player;
    private PlayerProfile profile;
    private QuestTrackingService tracking;
    private QuestAssignment first;
    private QuestAssignment second;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        first = QuestAssignment.create(playerId, TestFixtures.quest("first", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2), "daily", "p", Instant.now(), null);
        second = QuestAssignment.create(playerId, TestFixtures.quest("second", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2), "daily", "p", Instant.now(), null);
        profile = new PlayerProfile(playerId, "Tester", FeedbackPreferences.defaults(), null, 0, List.of(first, second));
        ProfileService profiles = mock(ProfileService.class);
        when(profiles.profile(player)).thenReturn(java.util.Optional.of(profile));
        when(profiles.onlineProfiles()).thenReturn(List.of(profile));
        ConfigManager configs = mock(ConfigManager.class);
        ConfigSnapshot snapshot = mock(ConfigSnapshot.class);
        QuestRegistrySnapshot registry = mock(QuestRegistrySnapshot.class);
        when(configs.snapshot()).thenReturn(snapshot);
        when(snapshot.registry()).thenReturn(registry);
        when(registry.quests()).thenReturn(Map.of("first", first.definition(), "second", second.definition()));
        tracking = new QuestTrackingService(configs, profiles);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void permissionRejectsBothTrackAndUntrackMutation() {
        when(player.hasPermission("plexonquests.pin")).thenReturn(false);
        assertEquals(QuestTrackingService.Result.NO_PERMISSION, tracking.track(player, "first"));
        profile.pinnedAssignment(first.id());
        assertEquals(QuestTrackingService.Result.NO_PERMISSION, tracking.untrack(player));
        assertTrue(profile.pinnedAssignment().filter(first.id()::equals).isPresent());
    }

    @Test
    void switchingAndCompletionCleanupUseOneAuthority() {
        when(player.hasPermission("plexonquests.pin")).thenReturn(true);
        assertEquals(QuestTrackingService.Result.TRACKED, tracking.track(player, "first"));
        assertEquals(QuestTrackingService.Result.TRACKED, tracking.track(player, "second"));
        assertTrue(profile.pinnedAssignment().filter(second.id()::equals).isPresent());

        second.addProgress("objective_1", 2, Instant.now());
        tracking.onComplete(new com.zpkdxgames.plexonquests.event.QuestCompleteEvent(player, second.id(), "second"));
        assertTrue(profile.pinnedAssignment().isEmpty());
    }

    @Test
    void reconcileClearsPersistedNonActiveTrackingWithoutCheckingPermission() {
        when(player.hasPermission("plexonquests.pin")).thenReturn(false);
        profile.pinnedAssignment(first.id());
        first.forceComplete(Instant.now());
        assertTrue(tracking.reconcile(player, profile));
        assertTrue(profile.pinnedAssignment().isEmpty());
    }
}
