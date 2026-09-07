package com.zpkdxgames.plexonquests.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalProgressContributionTest {
    @Test
    void metadataIsNormalizedAndImmutable() {
        ExternalProgressContribution contribution = new ExternalProgressContribution(
                ExternalObjectiveType.PLEXON_RANK_UP,
                1L,
                true,
                Map.of("Rank.To", "Technician-1"),
                "rankup:test");
        assertEquals("Technician-1", contribution.metadata().get("rank.to"));
        assertThrows(UnsupportedOperationException.class, () -> contribution.metadata().put("x", "y"));
    }

    @Test
    void oldConstructorRemainsAvailable() {
        ExternalProgressContribution contribution = new ExternalProgressContribution(
                ExternalObjectiveType.PLEXON_CRATE_OPEN, 1L, true, "crateopen:test");
        assertEquals(Map.of(), contribution.metadata());
    }
}
