package com.zpkdxgames.plexonquests.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.ConfigSnapshot;
import com.zpkdxgames.plexonquests.config.FlatConfiguration;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.rotation.RerollService;
import com.zpkdxgames.plexonquests.service.FeedbackPreferences;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class PlexonQuestsExpansionContractTest {
    private OfflinePlayer offline;
    private Player player;
    private PlayerProfile profile;
    private PlexonQuestsExpansion expansion;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        ConfigManager configs = mock(ConfigManager.class);
        ConfigSnapshot snapshot = mock(ConfigSnapshot.class);
        FlatConfiguration messages = mock(FlatConfiguration.class);
        when(configs.snapshot()).thenReturn(snapshot);
        when(snapshot.messages()).thenReturn(messages);
        when(messages.string("neutral-placeholder", "—")).thenReturn("—");

        player = mock(Player.class);
        offline = mock(OfflinePlayer.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(offline.isOnline()).thenReturn(true);
        when(offline.getPlayer()).thenReturn(player);

        QuestAssignment assignment = QuestAssignment.create(
                playerId,
                TestFixtures.quest("papi-tracked", CompletionMode.ALL, 2),
                "daily",
                "period",
                Instant.now(),
                Instant.now().plusSeconds(600));
        assignment.addProgress("objective_1", 1, Instant.now());
        profile = new PlayerProfile(playerId, "Tester", FeedbackPreferences.defaults(), assignment.id(), 7, List.of(assignment));

        ProfileService profiles = mock(ProfileService.class);
        when(profiles.profile(player)).thenReturn(java.util.Optional.of(profile));
        TextService text = mock(TextService.class);
        when(text.parse(anyString())).thenReturn(Component.text("Tracked Quest"));
        when(text.plain(any(Component.class))).thenReturn("Tracked Quest");
        when(text.formatNumber(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> Long.toString(invocation.getArgument(0)));

        expansion = new PlexonQuestsExpansion(
                configs,
                profiles,
                mock(IntegrationManager.class),
                mock(RerollService.class),
                text,
                "4.0.0-rc.1");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void oldPinnedPlaceholdersAndNewTrackedAliasesRemainEquivalent() {
        assertEquals(expansion.onRequest(offline, "pinned_name"), expansion.onRequest(offline, "tracked"));
        assertEquals(expansion.onRequest(offline, "pinned_progress"), expansion.onRequest(offline, "tracked_progress"));
        assertEquals(expansion.onRequest(offline, "pinned_percentage"), expansion.onRequest(offline, "tracked_percentage"));
        assertEquals(expansion.onRequest(offline, "active"), expansion.onRequest(offline, "active_count"));
        assertEquals("1", expansion.onRequest(offline, "active_count"));
    }

    @Test
    void staleCompletedTrackingUnknownIdsAndOfflinePlayersFailToNeutralValue() {
        QuestAssignment tracked = profile.pinnedAssignment().flatMap(profile::assignment).orElseThrow();
        tracked.forceComplete(Instant.now());
        assertEquals("—", expansion.onRequest(offline, "tracked"));
        assertEquals("—", expansion.onRequest(offline, "tracked_percentage"));
        assertEquals("—", expansion.onRequest(offline, "unknown_quest_state"));

        when(offline.isOnline()).thenReturn(false);
        assertEquals("—", expansion.onRequest(offline, "active_count"));
    }
}
