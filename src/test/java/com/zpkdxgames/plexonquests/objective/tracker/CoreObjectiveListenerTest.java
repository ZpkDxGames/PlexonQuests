package com.zpkdxgames.plexonquests.objective.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.BlockOriginService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class CoreObjectiveListenerTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void brewingExtractionCountsOnlyItemsActuallyRemoved() {
        ItemStack clicked = mock(ItemStack.class);
        when(clicked.getAmount()).thenReturn(7);
        when(clicked.getMaxStackSize()).thenReturn(64);

        ItemStack cursor = mock(ItemStack.class);
        when(cursor.getType()).thenReturn(Material.POTION);
        when(cursor.getAmount()).thenReturn(60);

        assertEquals(1, CoreObjectiveListener.removedAmount(InventoryAction.PICKUP_ONE, clicked, cursor, 0));
        assertEquals(4, CoreObjectiveListener.removedAmount(InventoryAction.PICKUP_HALF, clicked, cursor, 0));
        assertEquals(4, CoreObjectiveListener.removedAmount(InventoryAction.PICKUP_SOME, clicked, cursor, 0));
        assertEquals(3, CoreObjectiveListener.removedAmount(
                InventoryAction.MOVE_TO_OTHER_INVENTORY, clicked, cursor, 3));
        assertEquals(7, CoreObjectiveListener.removedAmount(InventoryAction.PICKUP_ALL, clicked, cursor, 0));
        assertEquals(0, CoreObjectiveListener.removedAmount(InventoryAction.NOTHING, clicked, cursor, 0));
    }

    @Test
    void blockBreakWithoutInterestSkipsQuestWorkButStillMaintainsOriginBookkeeping() {
        ProgressService progress = mock(ProgressService.class);
        BlockOriginService origins = mock(BlockOriginService.class);
        CoreObjectiveListener listener = listener(progress, origins);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.STONE);
        when(progress.interested(player, ObjectiveType.BREAK_BLOCK, Material.STONE)).thenReturn(false);

        listener.onBreak(event);

        verify(origins, never()).origin(block);
        verify(progress, never()).contribute(
                eq(player), org.mockito.ArgumentMatchers.any(Contribution.class));
        verify(origins).markBroken(block);
    }

    @Test
    void craftWithoutInterestReturnsBeforeInventoryCapacityScan() {
        ProgressService progress = mock(ProgressService.class);
        CoreObjectiveListener listener = listener(progress, mock(BlockOriginService.class));
        Player player = mock(Player.class);
        CraftItemEvent event = mock(CraftItemEvent.class);
        Recipe recipe = mock(Recipe.class);
        ItemStack result = mock(ItemStack.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRecipe()).thenReturn(recipe);
        when(recipe.getResult()).thenReturn(result);
        when(result.getType()).thenReturn(Material.DIAMOND);
        when(progress.interested(player, ObjectiveType.CRAFT_ITEM, Material.DIAMOND)).thenReturn(false);

        listener.onCraft(event);

        verify(player, never()).getInventory();
        verify(progress, never()).contribute(
                eq(player), org.mockito.ArgumentMatchers.any(Contribution.class));
    }

    @Test
    void brewingWithoutInterestReturnsBeforeInspectingInventoryView() {
        ProgressService progress = mock(ProgressService.class);
        CoreObjectiveListener listener = listener(progress, mock(BlockOriginService.class));
        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(progress.interested(player, ObjectiveType.BREW_POTION)).thenReturn(false);

        listener.onBrewingExtract(event);

        verify(event, never()).getView();
        verify(progress, never()).contribute(
                eq(player), org.mockito.ArgumentMatchers.any(Contribution.class));
    }

    @Test
    void deathWithoutInterestSkipsSpawnReasonPersistentDataRead() {
        ProgressService progress = mock(ProgressService.class);
        CoreObjectiveListener listener = listener(progress, mock(BlockOriginService.class));
        Player killer = mock(Player.class);
        LivingEntity entity = mock(LivingEntity.class);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(entity);
        when(entity.getKiller()).thenReturn(killer);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(progress.interested(killer, ObjectiveType.KILL_ENTITY, EntityType.ZOMBIE)).thenReturn(false);

        listener.onDeath(event);

        verify(entity, never()).getPersistentDataContainer();
        verify(progress, never()).contribute(
                eq(killer), org.mockito.ArgumentMatchers.any(Contribution.class));
    }

    private static CoreObjectiveListener listener(ProgressService progress, BlockOriginService origins) {
        return new CoreObjectiveListener(MockBukkit.createMockPlugin("PlexonQuestsTest"), progress, origins);
    }
}
