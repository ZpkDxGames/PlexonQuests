package com.zpkdxgames.plexonquests.objective.matcher;

import com.zpkdxgames.plexonquests.config.BlockOriginMode;
import com.zpkdxgames.plexonquests.config.PluginSettings;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.objective.ObjectiveFilters;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class ObjectiveMatcher {
    private ObjectiveMatcher() {}

    public static long acceptedAmount(
            Player player,
            ObjectiveDefinition objective,
            Contribution contribution,
            PluginSettings.Tracking global) {
        if (objective.type() != contribution.type() || contribution.amount() <= 0L) {
            return 0L;
        }
        if (contribution.gameMode() == GameMode.SPECTATOR && !global.spectatorCounts()) {
            return 0L;
        }
        if (contribution.gameMode() == GameMode.CREATIVE && !global.creativeCounts()) {
            return 0L;
        }
        ObjectiveFilters filters = objective.filters();
        if (!filters.gameModes().isEmpty() && !filters.gameModes().contains(contribution.gameMode())) {
            return 0L;
        }
        if (!filters.worlds().isEmpty() && !filters.worlds().contains(contribution.world())) {
            return 0L;
        }
        if (!filters.worldEnvironments().isEmpty()
                && !filters.worldEnvironments().contains(contribution.worldEnvironment())) {
            return 0L;
        }
        if (!filters.materials().isEmpty()
                && (contribution.material() == null || !filters.materials().contains(contribution.material()))) {
            return 0L;
        }
        if (!filters.caughtMaterials().isEmpty()
                && (contribution.material() == null || !filters.caughtMaterials().contains(contribution.material()))) {
            return 0L;
        }
        if (!filters.entityTypes().isEmpty()
                && (contribution.entityType() == null || !filters.entityTypes().contains(contribution.entityType()))) {
            return 0L;
        }
        if (!filters.damageCauses().isEmpty()
                && (contribution.damageCause() == null || !filters.damageCauses().contains(contribution.damageCause()))) {
            return 0L;
        }
        if (!filters.spawnReasons().isEmpty()
                && (contribution.spawnReason() == null || !filters.spawnReasons().contains(contribution.spawnReason()))) {
            return 0L;
        }
        if (global.originMode() != BlockOriginMode.OFF) {
            if (filters.origin() == OriginPolicy.NATURAL_ONLY
                    && (!contribution.originKnown() || !contribution.natural())) {
                return 0L;
            }
            if (filters.origin() == OriginPolicy.PLAYER_PLACED_ONLY
                    && (!contribution.originKnown() || contribution.natural())) {
                return 0L;
            }
        }
        if (filters.matureOnly() && !contribution.mature()) {
            return 0L;
        }
        if (filters.hostileOnly() && !contribution.hostile()) {
            return 0L;
        }
        if (filters.uniqueOnly() && !contribution.unique()) {
            return 0L;
        }
        if (filters.excludeTeleports() && contribution.teleport()) {
            return 0L;
        }
        if (!filters.movementTypes().isEmpty()
                && !filters.movementTypes().contains(contribution.movementType())) {
            return 0L;
        }
        if (!filters.advancementKeys().isEmpty()
                && !filters.advancementKeys().contains(contribution.advancementKey())) {
            return 0L;
        }
        for (String permission : filters.requiredPermissions()) {
            if (!player.hasPermission(permission)) {
                return 0L;
            }
        }
        for (String permission : filters.blockedPermissions()) {
            if (player.hasPermission(permission)) {
                return 0L;
            }
        }
        long amount = Math.min(contribution.amount(), filters.maximumContribution());
        return amount < filters.minimumContribution() ? 0L : amount;
    }
}
