package com.zpkdxgames.plexonquests.quest;

import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.reward.RewardBundle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record QuestDefinition(
        String id,
        int revision,
        boolean enabled,
        QuestScope scope,
        String category,
        String rarity,
        int weight,
        QuestEligibility eligibility,
        QuestDisplay display,
        CompletionMode completionMode,
        ClaimMode claimMode,
        Map<String, ObjectiveDefinition> objectives,
        RewardBundle rewards,
        String completeEffect,
        String claimEffect,
        String fingerprint,
        String source) {

    public QuestDefinition {
        id = Objects.requireNonNull(id, "id");
        revision = Math.max(1, revision);
        scope = Objects.requireNonNull(scope, "scope");
        category = Objects.requireNonNullElse(category, "general");
        rarity = Objects.requireNonNullElse(rarity, "COMMON");
        weight = Math.max(1, weight);
        eligibility = Objects.requireNonNull(eligibility, "eligibility");
        display = Objects.requireNonNull(display, "display");
        completionMode = Objects.requireNonNullElse(completionMode, CompletionMode.ALL);
        claimMode = Objects.requireNonNullElse(claimMode, ClaimMode.MANUAL);
        objectives = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(objectives));
        rewards = Objects.requireNonNull(rewards, "rewards");
        completeEffect = Objects.requireNonNullElse(completeEffect, "");
        claimEffect = Objects.requireNonNullElse(claimEffect, "quest-claim");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        source = Objects.requireNonNullElse(source, "unknown");
    }
}

