package com.zpkdxgames.plexonquests.integration.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class PlayerSkillsSnapshotTest {
    @Test
    void unavailableProviderIsSafeAndEmpty() {
        PlayerSkillsSnapshot snapshot = PlayerSkillsSnapshot.unavailable();
        assertFalse(snapshot.available());
        assertFalse(snapshot.ready());
        assertTrue(snapshot.skills().isEmpty());
    }

    @Test
    void presentButNotReadyProviderUsesLoadingState() {
        PlayerSkillsSnapshot snapshot = PlayerSkillsSnapshot.loading();
        assertTrue(snapshot.available());
        assertFalse(snapshot.ready());
        assertEquals(0, snapshot.totalLevel());
    }

    @Test
    void readySnapshotFindsSkillCaseInsensitively() {
        SkillSnapshot mining = new SkillSnapshot("MINING", "Mining", Material.IRON_PICKAXE,
                12, 345L, 500L, 0.69D);
        PlayerSkillsSnapshot snapshot = new PlayerSkillsSnapshot(true, true, 12, List.of(mining));
        assertTrue(snapshot.ready());
        assertEquals(12, snapshot.skill("mining").orElseThrow().level());
        assertTrue(snapshot.skill(null).isEmpty());
    }
}
