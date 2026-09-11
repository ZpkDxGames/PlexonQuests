package com.zpkdxgames.plexonquests.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RewardDeliveryOrderTest {
    @Test
    void commandStyleRewardsAreAlwaysAfterNonCommandRewards() {
        RewardDefinition command = reward("command", RewardType.COMMAND);
        RewardDefinition item = reward("item", RewardType.ITEM);
        RewardDefinition money = reward("money", RewardType.MONEY);
        RewardDefinition key = reward("key", RewardType.PLEXON_KEY);

        List<RewardDefinition> ordered = RewardService.orderForDelivery(List.of(command, item, key, money));

        assertEquals(List.of(item, money, command, key), ordered);
    }

    private static RewardDefinition reward(String id, RewardType type) {
        return new RewardDefinition(id, type, 1L, 1D, org.bukkit.Material.STONE,
                type == RewardType.COMMAND ? "say test" : "", "", Duration.ZERO, "", "",
                type == RewardType.PLEXON_KEY ? "say key" : "", id, 1);
    }
}
