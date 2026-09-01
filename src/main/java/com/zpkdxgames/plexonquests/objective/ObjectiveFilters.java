package com.zpkdxgames.plexonquests.objective;

import java.util.Map;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public record ObjectiveFilters(
        Set<Material> materials,
        Set<Material> caughtMaterials,
        Set<EntityType> entityTypes,
        Set<EntityDamageEvent.DamageCause> damageCauses,
        Set<CreatureSpawnEvent.SpawnReason> spawnReasons,
        Set<GameMode> gameModes,
        Set<String> worlds,
        Set<World.Environment> worldEnvironments,
        Set<String> movementTypes,
        Set<String> advancementKeys,
        Set<String> requiredPermissions,
        Set<String> blockedPermissions,
        OriginPolicy origin,
        boolean matureOnly,
        boolean hostileOnly,
        boolean uniqueOnly,
        boolean excludeTeleports,
        long minimumContribution,
        long maximumContribution,
        long cooldownMillis,
        Map<String, String> extras) {

    public ObjectiveFilters {
        materials = Set.copyOf(materials);
        caughtMaterials = Set.copyOf(caughtMaterials);
        entityTypes = Set.copyOf(entityTypes);
        damageCauses = Set.copyOf(damageCauses);
        spawnReasons = Set.copyOf(spawnReasons);
        gameModes = Set.copyOf(gameModes);
        worlds = Set.copyOf(worlds);
        worldEnvironments = Set.copyOf(worldEnvironments);
        movementTypes = Set.copyOf(movementTypes);
        advancementKeys = Set.copyOf(advancementKeys);
        requiredPermissions = Set.copyOf(requiredPermissions);
        blockedPermissions = Set.copyOf(blockedPermissions);
        origin = origin == null ? OriginPolicy.ANY : origin;
        minimumContribution = Math.max(0L, minimumContribution);
        maximumContribution = maximumContribution <= 0L ? Long.MAX_VALUE : maximumContribution;
        cooldownMillis = Math.max(0L, cooldownMillis);
        extras = Map.copyOf(extras);
    }

    public static ObjectiveFilters empty() {
        return new ObjectiveFilters(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), OriginPolicy.ANY, false, false, false, true,
                0L, Long.MAX_VALUE, 0L, Map.of());
    }
}

