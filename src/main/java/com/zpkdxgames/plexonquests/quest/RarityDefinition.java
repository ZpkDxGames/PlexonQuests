package com.zpkdxgames.plexonquests.quest;

import org.bukkit.Material;

public record RarityDefinition(
        String id,
        String display,
        String color,
        Material icon,
        boolean glow,
        int sortPriority,
        String completeEffect) {}

