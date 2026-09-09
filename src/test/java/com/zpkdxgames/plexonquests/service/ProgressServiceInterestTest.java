package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.objective.ObjectiveFilters;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class ProgressServiceInterestTest {
    @Test
    void noActiveObjectivesExposeNoInterest() {
        Harness harness = harness(List.of());

        assertFalse(harness.service().interested(harness.player(), ObjectiveType.BREAK_BLOCK));
        assertFalse(harness.service().interested(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.STONE));
    }

    @Test
    void wildcardMaterialObjectiveMatchesWithoutAllocatingPerEventState() {
        QuestAssignment assignment = assignment(
                ObjectiveType.BREAK_BLOCK, Set.of(), Set.of(), OriginPolicy.ANY, false, Set.of(), false, false);
        Harness harness = harness(List.of(assignment));

        assertTrue(harness.service().interested(harness.player(), ObjectiveType.BREAK_BLOCK));
        assertTrue(harness.service().interested(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.STONE));
        assertTrue(harness.service().interested(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.DIRT));
    }

    @Test
    void materialSpecificObjectiveRejectsWrongMaterial() {
        QuestAssignment assignment = assignment(
                ObjectiveType.BREAK_BLOCK,
                Set.of(Material.STONE),
                Set.of(),
                OriginPolicy.ANY,
                false,
                Set.of(),
                false,
                false);
        Harness harness = harness(List.of(assignment));

        assertTrue(harness.service().interested(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.STONE));
        assertFalse(harness.service().interested(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.DIRT));
    }

    @Test
    void entitySpecificObjectiveRejectsWrongEntity() {
        QuestAssignment assignment = assignment(
                ObjectiveType.KILL_ENTITY,
                Set.of(),
                Set.of(EntityType.ZOMBIE),
                OriginPolicy.ANY,
                false,
                Set.of(),
                false,
                false);
        Harness harness = harness(List.of(assignment));

        assertTrue(harness.service().interested(
                harness.player(), ObjectiveType.KILL_ENTITY, EntityType.ZOMBIE));
        assertFalse(harness.service().interested(
                harness.player(), ObjectiveType.KILL_ENTITY, EntityType.COW));
    }

    @Test
    void completedObjectiveIsRemovedFromActiveInterest() {
        QuestAssignment assignment = assignment(
                ObjectiveType.BREAK_BLOCK, Set.of(), Set.of(), OriginPolicy.ANY, false, Set.of(), true, false);
        Harness harness = harness(List.of(assignment));

        assertFalse(harness.service().interested(harness.player(), ObjectiveType.BREAK_BLOCK));
    }

    @Test
    void rerollReservedAssignmentIsIgnored() {
        QuestAssignment assignment = assignment(
                ObjectiveType.BREAK_BLOCK, Set.of(), Set.of(), OriginPolicy.ANY, false, Set.of(), false, true);
        Harness harness = harness(List.of(assignment));

        assertFalse(harness.service().interested(harness.player(), ObjectiveType.BREAK_BLOCK));
    }

    @Test
    void requirementProfilesExposeOnlyExpensiveContextActuallyNeeded() {
        QuestAssignment blockAssignment = assignment(
                ObjectiveType.BREAK_BLOCK,
                Set.of(Material.STONE),
                Set.of(),
                OriginPolicy.NATURAL_ONLY,
                true,
                Set.of(),
                false,
                false);
        QuestAssignment killAssignment = assignment(
                ObjectiveType.KILL_ENTITY,
                Set.of(),
                Set.of(EntityType.ZOMBIE),
                OriginPolicy.ANY,
                false,
                Set.of(CreatureSpawnEvent.SpawnReason.SPAWNER),
                false,
                false);
        Harness harness = harness(List.of(blockAssignment, killAssignment));

        assertTrue(harness.service().requiresOrigin(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.STONE));
        assertTrue(harness.service().requiresMaturity(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.STONE));
        assertFalse(harness.service().requiresOrigin(
                harness.player(), ObjectiveType.BREAK_BLOCK, Material.DIRT));
        assertTrue(harness.service().requiresSpawnReason(
                harness.player(), ObjectiveType.KILL_ENTITY, EntityType.ZOMBIE));
        assertFalse(harness.service().requiresSpawnReason(
                harness.player(), ObjectiveType.KILL_ENTITY, EntityType.COW));
    }

    private static Harness harness(List<QuestAssignment> assignments) {
        UUID playerId = UUID.randomUUID();
        JavaPlugin plugin = mock(JavaPlugin.class);
        ProfileService profiles = mock(ProfileService.class);
        StorageService storage = mock(StorageService.class);
        ConfigManager configs = mock(ConfigManager.class);
        ProgressService service = new ProgressService(plugin, profiles, storage, configs);

        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.playerId()).thenReturn(playerId);
        when(profile.assignments()).thenReturn(assignments);
        service.reindex(profile);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return new Harness(service, player);
    }

    private static QuestAssignment assignment(
            ObjectiveType type,
            Set<Material> materials,
            Set<EntityType> entities,
            OriginPolicy origin,
            boolean matureOnly,
            Set<CreatureSpawnEvent.SpawnReason> spawnReasons,
            boolean complete,
            boolean rerollReserved) {
        ObjectiveFilters filters = mock(ObjectiveFilters.class);
        when(filters.materials()).thenReturn(materials);
        when(filters.caughtMaterials()).thenReturn(Set.of());
        when(filters.entityTypes()).thenReturn(entities);
        when(filters.origin()).thenReturn(origin);
        when(filters.matureOnly()).thenReturn(matureOnly);
        when(filters.spawnReasons()).thenReturn(spawnReasons);

        ObjectiveDefinition definition = mock(ObjectiveDefinition.class);
        when(definition.id()).thenReturn("objective");
        when(definition.type()).thenReturn(type);
        when(definition.filters()).thenReturn(filters);

        ObjectiveProgress progress = mock(ObjectiveProgress.class);
        when(progress.definition()).thenReturn(definition);
        when(progress.complete()).thenReturn(complete);

        QuestAssignment assignment = mock(QuestAssignment.class);
        when(assignment.id()).thenReturn(UUID.randomUUID());
        when(assignment.state()).thenReturn(AssignmentState.ACTIVE);
        when(assignment.rerollReserved()).thenReturn(rerollReserved);
        when(assignment.objectives()).thenReturn(List.of(progress));
        return assignment;
    }

    private record Harness(ProgressService service, Player player) {}
}
