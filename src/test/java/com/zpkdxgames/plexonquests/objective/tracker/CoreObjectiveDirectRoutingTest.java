package com.zpkdxgames.plexonquests.objective.tracker;

import com.zpkdxgames.plexonquests.integration.core.CoreRuntimeCoordinator;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.service.BlockOriginService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreObjectiveDirectRoutingTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void authoritativeCoreModeSkipsSecondBukkitBreakPass() {
        ProgressService progress = mock(ProgressService.class);
        BlockOriginService origins = mock(BlockOriginService.class);
        CoreRuntimeCoordinator runtime = mock(CoreRuntimeCoordinator.class);
        when(runtime.directCommittedRouting()).thenReturn(true);

        CoreObjectiveListener listener = new CoreObjectiveListener(
                MockBukkit.createMockPlugin("PlexonQuestsTest"),
                progress,
                origins,
                null,
                runtime,
                null);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.isCancelled()).thenReturn(false);

        listener.onBreak(event);

        verify(runtime, never()).consume(event);
        verify(progress, never()).contribute(any(Player.class), any(Contribution.class));
        verify(origins, never()).origin(any(Block.class));
        verify(origins, never()).markBroken(any(Block.class));
    }
}
