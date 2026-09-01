package com.zpkdxgames.plexonquests.objective;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public record Contribution(
        ObjectiveType type,
        long amount,
        Material material,
        EntityType entityType,
        EntityDamageEvent.DamageCause damageCause,
        CreatureSpawnEvent.SpawnReason spawnReason,
        String world,
        World.Environment worldEnvironment,
        GameMode gameMode,
        boolean originKnown,
        boolean natural,
        boolean mature,
        boolean hostile,
        boolean teleport,
        boolean unique,
        String movementType,
        String advancementKey,
        String sourceToken) {

    public static Contribution simple(ObjectiveType type, long amount, Player player) {
        return new Contribution(
                type,
                amount,
                null,
                null,
                null,
                null,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                false,
                false,
                true,
                false,
                false,
                true,
                "",
                "",
                "");
    }
}

