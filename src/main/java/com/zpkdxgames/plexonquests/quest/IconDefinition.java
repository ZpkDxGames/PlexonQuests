package com.zpkdxgames.plexonquests.quest;

import java.util.Objects;
import org.bukkit.Material;

public record IconDefinition(
        Material material,
        int amount,
        boolean glowWhenComplete,
        Integer customModelData,
        String texture,
        String serializedItem) {

    public IconDefinition {
        material = Objects.requireNonNullElse(material, Material.PAPER);
        amount = Math.max(1, Math.min(64, amount));
        texture = Objects.requireNonNullElse(texture, "");
        serializedItem = Objects.requireNonNullElse(serializedItem, "");
    }

    public static IconDefinition of(Material material) {
        return new IconDefinition(material, 1, true, null, "", "");
    }
}
