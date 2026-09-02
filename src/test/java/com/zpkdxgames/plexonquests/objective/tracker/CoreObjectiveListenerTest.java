package com.zpkdxgames.plexonquests.objective.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CoreObjectiveListenerTest {
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
}
