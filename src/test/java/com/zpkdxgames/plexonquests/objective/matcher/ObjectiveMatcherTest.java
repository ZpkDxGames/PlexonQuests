package com.zpkdxgames.plexonquests.objective.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.zpkdxgames.plexonquests.config.BlockOriginMode;
import com.zpkdxgames.plexonquests.config.PluginSettings;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.objective.ObjectiveFilters;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ObjectiveMatcherTest {
    @Test
    void offModeIgnoresNaturalAndPlayerPlacedOriginFilters() {
        Player player = mock(Player.class);
        Contribution unknown = contribution(false, false);

        assertEquals(1L, ObjectiveMatcher.acceptedAmount(
                player, objective(OriginPolicy.NATURAL_ONLY), unknown, tracking(BlockOriginMode.OFF)));
        assertEquals(1L, ObjectiveMatcher.acceptedAmount(
                player, objective(OriginPolicy.PLAYER_PLACED_ONLY), unknown, tracking(BlockOriginMode.OFF)));
    }

    @Test
    void trackedModesFailClosedWhenBlockOriginIsUnknown() {
        Player player = mock(Player.class);
        Contribution unknown = contribution(false, false);

        assertEquals(0L, ObjectiveMatcher.acceptedAmount(
                player,
                objective(OriginPolicy.NATURAL_ONLY),
                unknown,
                tracking(BlockOriginMode.PERSISTENT_CHUNK)));
        assertEquals(0L, ObjectiveMatcher.acceptedAmount(
                player,
                objective(OriginPolicy.PLAYER_PLACED_ONLY),
                unknown,
                tracking(BlockOriginMode.SESSION)));
    }

    private static ObjectiveDefinition objective(OriginPolicy origin) {
        ObjectiveFilters filters = new ObjectiveFilters(
                Set.of(Material.STONE),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(GameMode.SURVIVAL),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                origin,
                false,
                false,
                false,
                true,
                0L,
                Long.MAX_VALUE,
                0L,
                Map.of());
        return new ObjectiveDefinition("stone", ObjectiveType.BREAK_BLOCK, 1L, "Stone", filters);
    }

    private static Contribution contribution(boolean known, boolean natural) {
        return new Contribution(
                ObjectiveType.BREAK_BLOCK,
                1L,
                Material.STONE,
                null,
                null,
                null,
                "world",
                World.Environment.NORMAL,
                GameMode.SURVIVAL,
                known,
                natural,
                true,
                false,
                false,
                true,
                "",
                "",
                "");
    }

    private static PluginSettings.Tracking tracking(BlockOriginMode mode) {
        return new PluginSettings.Tracking(
                false,
                false,
                mode,
                65_536,
                20L,
                32D,
                10L,
                10L,
                Duration.ofSeconds(4),
                Duration.ofMinutes(5),
                0L);
    }
}
