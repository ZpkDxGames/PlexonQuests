package com.zpkdxgames.plexonquests.quest;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PoolDefinition(
        String id,
        boolean enabled,
        QuestScope scope,
        int baseAssignments,
        boolean preventDuplicates,
        Duration recentHistoryExclusion,
        List<String> guaranteedCategories,
        int maximumPerCategory,
        Set<String> requiredPermissions,
        Set<String> blockedPermissions,
        Set<String> rankCategories,
        Set<String> worlds,
        Set<String> requiredIntegrations,
        Map<String, Integer> questWeights,
        String source) {

    public PoolDefinition {
        baseAssignments = Math.max(0, baseAssignments);
        recentHistoryExclusion = recentHistoryExclusion == null ? Duration.ZERO : recentHistoryExclusion;
        guaranteedCategories = List.copyOf(guaranteedCategories);
        maximumPerCategory = maximumPerCategory <= 0 ? Integer.MAX_VALUE : maximumPerCategory;
        requiredPermissions = Set.copyOf(requiredPermissions);
        blockedPermissions = Set.copyOf(blockedPermissions);
        rankCategories = Set.copyOf(rankCategories);
        worlds = Set.copyOf(worlds);
        requiredIntegrations = Set.copyOf(requiredIntegrations);
        questWeights = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(questWeights));
    }
}

