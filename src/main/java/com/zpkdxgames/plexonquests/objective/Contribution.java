package com.zpkdxgames.plexonquests.objective;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        Map<String, String> metadata,
        String sourceToken) {

    public Contribution {
        if (metadata == null || metadata.isEmpty()) {
            metadata = Map.of();
        } else {
            Map<String, String> normalized = new LinkedHashMap<>(metadata.size());
            metadata.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    normalized.put(
                            key.trim().toLowerCase(Locale.ROOT),
                            Objects.requireNonNullElse(value, "").trim());
                }
            });
            metadata = normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
        }
        sourceToken = Objects.requireNonNullElse(sourceToken, "");
    }

    /** Compatibility constructor retained for core 2.x listeners and external source compatibility. */
    public Contribution(
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
        this(
                type, amount, material, entityType, damageCause, spawnReason, world, worldEnvironment, gameMode,
                originKnown, natural, mature, hostile, teleport, unique, movementType, advancementKey,
                Map.of(), sourceToken);
    }

    public static Contribution simple(ObjectiveType type, long amount, Player player) {
        return integration(type, amount, player, Map.of(), "");
    }

    public static Contribution integration(
            ObjectiveType type,
            long amount,
            Player player,
            Map<String, String> metadata,
            String sourceToken) {
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
                metadata,
                sourceToken);
    }
}
