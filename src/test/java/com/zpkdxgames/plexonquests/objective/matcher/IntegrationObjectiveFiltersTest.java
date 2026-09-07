package com.zpkdxgames.plexonquests.objective.matcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationObjectiveFiltersTest {
    @Test
    void rankMetadataMatchesNormalizedFilters() {
        assertTrue(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_RANK_UP,
                Map.of("to-rank", "Technician-1", "to-category", "TECHNICIAN"),
                Map.of("rank.to", "technician-1", "rank.category.to", "technician")));
    }

    @Test
    void missingMetadataFailsClosed() {
        assertFalse(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_KEY_EARN,
                Map.of("key-category", "epic"),
                Map.of()));
    }

    @Test
    void numericThresholdsAreEnforced() {
        assertTrue(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_TOOL_LEVEL_UP,
                Map.of("minimum-level", "50"),
                Map.of("tool.level.new", "51")));
        assertFalse(IntegrationObjectiveFilters.matches(
                ObjectiveType.PLEXON_TOOL_LEVEL_UP,
                Map.of("minimum-level", "50"),
                Map.of("tool.level.new", "49")));
    }

    @Test
    void unsupportedIntegrationFilterIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> IntegrationObjectiveFilters.validate(
                ObjectiveType.PLEXON_KEY_EARN, Map.of("to-rank", "technician")));
    }
}
