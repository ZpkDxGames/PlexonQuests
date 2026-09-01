package com.zpkdxgames.plexonquests.quest;

import java.util.Set;

public record QuestEligibility(
        String requiredPermission,
        Set<String> blockedPermissions,
        Set<String> rankCategories,
        Set<String> worlds,
        Set<String> requiredIntegrations) {

    public QuestEligibility {
        requiredPermission = requiredPermission == null ? "" : requiredPermission;
        blockedPermissions = Set.copyOf(blockedPermissions);
        rankCategories = Set.copyOf(rankCategories);
        worlds = Set.copyOf(worlds);
        requiredIntegrations = Set.copyOf(requiredIntegrations);
    }
}

