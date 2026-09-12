package com.zpkdxgames.plexonquests.integration.skills;

import org.bukkit.Material;

public record SkillSnapshot(
        String id,
        String displayName,
        Material icon,
        int level,
        long totalXp,
        long xpForNextLevel,
        double progress) {}
