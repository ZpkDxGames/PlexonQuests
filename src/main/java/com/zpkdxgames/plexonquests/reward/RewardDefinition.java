package com.zpkdxgames.plexonquests.reward;

import java.time.Duration;
import java.util.Objects;
import org.bukkit.Material;

public record RewardDefinition(
        String id,
        RewardType type,
        long amount,
        double decimalAmount,
        Material material,
        String command,
        String permission,
        Duration permissionDuration,
        String keyCategory,
        String fallbackCommand,
        String serializedItem,
        String display,
        int weight) {

    public RewardDefinition {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        amount = Math.max(0L, amount);
        decimalAmount = Math.max(0D, decimalAmount);
        command = Objects.requireNonNullElse(command, "");
        permission = Objects.requireNonNullElse(permission, "");
        permissionDuration = permissionDuration == null ? Duration.ZERO : permissionDuration;
        keyCategory = Objects.requireNonNullElse(keyCategory, "");
        fallbackCommand = Objects.requireNonNullElse(fallbackCommand, "");
        serializedItem = Objects.requireNonNullElse(serializedItem, "");
        display = Objects.requireNonNullElse(display, id);
        weight = Math.max(1, weight);
    }
}

