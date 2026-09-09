package com.zpkdxgames.plexonquests.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antondev.crates.api.event.CrateOpenEvent;
import com.antondev.crates.api.event.CrateOpenEvent.OpeningPlan;
import com.plexon.tools.event.PlexonToolLevelUpEvent;
import com.plexon.tools.event.PlexonToolProgressEvent;
import com.zpkdxgames.plexondailyrewards.event.DailyRewardClaimedEvent;
import com.zpkdxgames.plexonquests.PlexonQuestsPlugin;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.rotation.RotationService;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import com.zpkdxgames.plexonranks.event.PlexonRankupEvent;
import com.zpkdxgames.plexonranks.event.PlexonRankupEvent.Rank;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class PlexonIntegrationAdaptersTest {
    private ServerMock server;
    private PlexonQuestsPlugin plugin;
    private ConfigManager configs;
    private ProgressService progress;
    private ProfileService profiles;
    private RotationService rotations;
    private PlayerMock player;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        configs = new ConfigManager(plugin);
        configs.loadInitial();
        progress = mock(ProgressService.class);
        profiles = mock(ProfileService.class);
        rotations = mock(RotationService.class);
        player = server.addPlayer();
        when(progress.interested(eq(player), any(ObjectiveType.class))).thenReturn(true);
        when(profiles.profile(player)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rankupEventContributesExactlyOnceWithTransactionMetadata() {
        MockBukkit.createMockPlugin("PlexonRanks");
        adapter("PLEXON_RANKS").register(context());

        Bukkit.getPluginManager().callEvent(new PlexonRankupEvent(
                player, new Rank("newbie-x"), new Rank("tinkerer-i"), "tx-rank-1"));

        Contribution contribution = captureSingleContribution();
        assertEquals(ObjectiveType.PLEXON_RANK_UP, contribution.type());
        assertEquals(1L, contribution.amount());
        assertEquals("newbie-x", contribution.metadata().get("rank.from"));
        assertEquals("tinkerer-i", contribution.metadata().get("rank.to"));
        assertEquals("tx-rank-1", contribution.metadata().get("rank.transaction"));
        assertEquals("rankup:tx-rank-1", contribution.sourceToken());
    }

    @Test
    void crateOpenEventContributesExactlyOnceWithOpeningMetadata() {
        MockBukkit.createMockPlugin("PlexonCrates");
        adapter("PLEXON_CRATES").register(context());

        Bukkit.getPluginManager().callEvent(new CrateOpenEvent(player, new OpeningPlan(
                "tx-crate-1", "Epic", "Rare-Key", 2L, List.of("Diamond", "Emerald"), "GUI")));

        Contribution contribution = captureSingleContribution();
        assertEquals(ObjectiveType.PLEXON_CRATE_OPEN, contribution.type());
        assertEquals(2L, contribution.amount());
        assertEquals("epic", contribution.metadata().get("crate.id"));
        assertEquals("rare-key", contribution.metadata().get("crate.key"));
        assertEquals("diamond,emerald", contribution.metadata().get("crate.reward"));
        assertEquals("gui", contribution.metadata().get("crate.source"));
        assertEquals("crateopen:tx-crate-1", contribution.sourceToken());
    }

    @Test
    void plexonToolsProgressPreservesCoalescedAmountExactly() {
        MockBukkit.createMockPlugin("PlexonTools");
        adapter("PLEXON_TOOLS").register(context());

        Bukkit.getPluginManager().callEvent(new PlexonToolProgressEvent(
                player,
                "legendary_pickaxe",
                12L,
                8L,
                "pickaxe",
                "blocks",
                Material.DIAMOND_ORE,
                "tools-progress-12"));

        Contribution contribution = captureSingleContribution();
        assertEquals(ObjectiveType.PLEXON_TOOL_PROGRESS, contribution.type());
        assertEquals(12L, contribution.amount());
        assertEquals("legendary_pickaxe", contribution.metadata().get("tool.id"));
        assertEquals("pickaxe", contribution.metadata().get("tool.category"));
        assertEquals("blocks", contribution.metadata().get("tool.progress.type"));
        assertEquals("diamond_ore", contribution.metadata().get("tool.material"));
        assertEquals("8", contribution.metadata().get("tool.level.new"));
        assertEquals("toolprogress:tools-progress-12", contribution.sourceToken());
    }

    @Test
    void plexonToolsLevelEventPreservesMultiLevelDelta() {
        MockBukkit.createMockPlugin("PlexonTools");
        adapter("PLEXON_TOOLS").register(context());

        Bukkit.getPluginManager().callEvent(new PlexonToolLevelUpEvent(
                player, "legendary_pickaxe", 3L, 7L, "pickaxe", "tools-level-4"));

        Contribution contribution = captureSingleContribution();
        assertEquals(ObjectiveType.PLEXON_TOOL_LEVEL_UP, contribution.type());
        assertEquals(4L, contribution.amount());
        assertEquals("3", contribution.metadata().get("tool.level.old"));
        assertEquals("7", contribution.metadata().get("tool.level.new"));
        assertEquals("toollevel:tools-level-4", contribution.sourceToken());
    }

    @Test
    void plexonToolsProgressWithNoInterestSkipsContributionEntirely() {
        when(progress.interested(player, ObjectiveType.PLEXON_TOOL_PROGRESS)).thenReturn(false);
        MockBukkit.createMockPlugin("PlexonTools");
        adapter("PLEXON_TOOLS").register(context());

        Bukkit.getPluginManager().callEvent(new PlexonToolProgressEvent(
                player,
                "legendary_pickaxe",
                20L,
                9L,
                "pickaxe",
                "blocks",
                Material.STONE,
                "ignored-event"));

        verify(progress).interested(player, ObjectiveType.PLEXON_TOOL_PROGRESS);
        verify(progress, never()).contribute(eq(player), any(Contribution.class));
    }

    @Test
    void dailyRewardClaimContributesExactlyOnceWithTierAndDayMetadata() {
        MockBukkit.createMockPlugin("PlexonDailyRewards");
        adapter("PLEXON_DAILY_REWARDS").register(context());

        Bukkit.getPluginManager().callEvent(new DailyRewardClaimedEvent(player, "Basic", 3, false));

        Contribution contribution = captureSingleContribution();
        assertEquals(ObjectiveType.PLEXON_DAILY_REWARD_CLAIM, contribution.type());
        assertEquals(1L, contribution.amount());
        assertEquals("basic", contribution.metadata().get("daily.tier"));
        assertEquals("3", contribution.metadata().get("daily.day"));
        assertEquals("false", contribution.metadata().get("daily.forced"));
        assertTrue(contribution.sourceToken().startsWith("dailyclaim:" + player.getUniqueId() + ":"));
        assertTrue(contribution.sourceToken().endsWith(":basic:3"));
    }

    private IntegrationContext context() {
        return new IntegrationContext(plugin, configs, progress, profiles, rotations);
    }

    private static IntegrationAdapter adapter(String id) {
        return PlexonIntegrationAdapters.all().stream()
                .filter(adapter -> adapter.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private Contribution captureSingleContribution() {
        ArgumentCaptor<Contribution> captor = ArgumentCaptor.forClass(Contribution.class);
        verify(progress, times(1)).contribute(eq(player), captor.capture());
        return captor.getValue();
    }
}
