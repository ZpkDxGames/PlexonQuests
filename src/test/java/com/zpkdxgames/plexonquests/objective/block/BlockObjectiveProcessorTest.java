package com.zpkdxgames.plexonquests.objective.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BlockObjectiveProcessorTest {
    @Test
    void uninterestedPlayerDoesNotResolveMaturityOriginOrAllocateContribution() {
        ProgressService progress = mock(ProgressService.class);
        Player player = mock(Player.class);
        BlockObjectiveProcessor processor = new BlockObjectiveProcessor(progress);
        AtomicBoolean maturityRead = new AtomicBoolean();
        AtomicBoolean originRead = new AtomicBoolean();

        processor.breakBlock(
                player,
                Material.STONE,
                () -> {
                    maturityRead.set(true);
                    return true;
                },
                () -> {
                    originRead.set(true);
                    return BlockObjectiveProcessor.OriginState.NATURAL;
                });

        assertFalse(maturityRead.get());
        assertFalse(originRead.get());
        verify(progress, never()).contribute(
                org.mockito.ArgumentMatchers.any(Player.class),
                org.mockito.ArgumentMatchers.any(Contribution.class));
    }

    @Test
    void naturalOriginIsResolvedOnlyWhenInterestedObjectiveRequiresIt() {
        ProgressService progress = mock(ProgressService.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(progress.interested(player, ObjectiveType.BREAK_BLOCK, Material.STONE)).thenReturn(true);
        when(progress.requiresOrigin(player, ObjectiveType.BREAK_BLOCK, Material.STONE)).thenReturn(true);
        BlockObjectiveProcessor processor = new BlockObjectiveProcessor(progress);
        AtomicBoolean originRead = new AtomicBoolean();

        processor.breakBlock(
                player,
                Material.STONE,
                () -> true,
                () -> {
                    originRead.set(true);
                    return BlockObjectiveProcessor.OriginState.NATURAL;
                });

        assertTrue(originRead.get());
        ArgumentCaptor<Contribution> contribution = ArgumentCaptor.forClass(Contribution.class);
        verify(progress).contribute(org.mockito.ArgumentMatchers.eq(player), contribution.capture());
        assertTrue(contribution.getValue().originKnown());
        assertTrue(contribution.getValue().natural());
        assertEquals(1L, contribution.getValue().amount());
    }

    @Test
    void matureCropCanProgressBreakAndHarvestExactlyOnceEach() {
        ProgressService progress = mock(ProgressService.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(progress.interested(player, ObjectiveType.BREAK_BLOCK, Material.WHEAT)).thenReturn(true);
        when(progress.interested(player, ObjectiveType.HARVEST_CROP, Material.WHEAT)).thenReturn(true);
        BlockObjectiveProcessor processor = new BlockObjectiveProcessor(progress);

        processor.breakBlock(
                player,
                Material.WHEAT,
                () -> true,
                () -> BlockObjectiveProcessor.OriginState.UNKNOWN);

        ArgumentCaptor<Contribution> contribution = ArgumentCaptor.forClass(Contribution.class);
        verify(progress, org.mockito.Mockito.times(2))
                .contribute(org.mockito.ArgumentMatchers.eq(player), contribution.capture());
        assertEquals(ObjectiveType.BREAK_BLOCK, contribution.getAllValues().get(0).type());
        assertEquals(ObjectiveType.HARVEST_CROP, contribution.getAllValues().get(1).type());
        assertEquals(1L, contribution.getAllValues().get(0).amount());
        assertEquals(1L, contribution.getAllValues().get(1).amount());
    }
}
