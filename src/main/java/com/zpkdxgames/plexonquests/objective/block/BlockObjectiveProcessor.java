package com.zpkdxgames.plexonquests.objective.block;

import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Source-independent block objective domain processor shared by local and Core-backed acquisition. */
public final class BlockObjectiveProcessor {
    private final ProgressService progress;

    public BlockObjectiveProcessor(ProgressService progress) {
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    public void breakBlock(
            Player player,
            Material material,
            Supplier<Boolean> maturity,
            Supplier<OriginState> origin) {
        boolean breakInterested = progress.interested(player, ObjectiveType.BREAK_BLOCK, material);
        boolean crop = isCrop(material);
        boolean harvestInterested = crop && progress.interested(player, ObjectiveType.HARVEST_CROP, material);
        if (!breakInterested && !harvestInterested) {
            return;
        }

        boolean needsMaturity = harvestInterested
                || (breakInterested && progress.requiresMaturity(player, ObjectiveType.BREAK_BLOCK, material));
        boolean mature = !needsMaturity || maturity.get();

        boolean needsOrigin = (breakInterested
                        && progress.requiresOrigin(player, ObjectiveType.BREAK_BLOCK, material))
                || (harvestInterested && progress.requiresOrigin(player, ObjectiveType.HARVEST_CROP, material));
        OriginState resolvedOrigin = needsOrigin ? origin.get() : OriginState.UNKNOWN;

        if (breakInterested) {
            progress.contribute(player, contribution(
                    ObjectiveType.BREAK_BLOCK, player, material, resolvedOrigin, mature));
        }
        if (harvestInterested && mature) {
            progress.contribute(player, contribution(
                    ObjectiveType.HARVEST_CROP, player, material, resolvedOrigin, true));
        }
    }

    public void placeBlock(Player player, Material material) {
        if (!progress.interested(player, ObjectiveType.PLACE_BLOCK, material)) {
            return;
        }
        progress.contribute(player, new Contribution(
                ObjectiveType.PLACE_BLOCK,
                1L,
                material,
                null,
                null,
                null,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                true,
                false,
                true,
                false,
                false,
                true,
                "",
                "",
                ""));
    }

    private static Contribution contribution(
            ObjectiveType type,
            Player player,
            Material material,
            OriginState origin,
            boolean mature) {
        return new Contribution(
                type,
                1L,
                material,
                null,
                null,
                null,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                origin.known(),
                origin.natural(),
                mature,
                false,
                false,
                true,
                "",
                "",
                "");
    }

    public static boolean isCrop(Material material) {
        return material == Material.WHEAT
                || material == Material.CARROTS
                || material == Material.POTATOES
                || material == Material.BEETROOTS
                || material == Material.NETHER_WART;
    }

    public record OriginState(boolean known, boolean natural) {
        public static final OriginState NATURAL = new OriginState(true, true);
        public static final OriginState PLAYER_PLACED = new OriginState(true, false);
        public static final OriginState UNKNOWN = new OriginState(false, false);
    }
}
