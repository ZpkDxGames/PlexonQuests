package com.zpkdxgames.plexonquests.quest;

import com.zpkdxgames.plexonquests.config.ValidationIssue;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record QuestRegistrySnapshot(
        Map<String, QuestDefinition> quests,
        Map<String, PoolDefinition> pools,
        Map<String, RarityDefinition> rarities,
        List<ValidationIssue> issues,
        Instant loadedAt) {

    public QuestRegistrySnapshot {
        quests = Map.copyOf(quests);
        pools = Map.copyOf(pools);
        rarities = Map.copyOf(rarities);
        issues = List.copyOf(issues);
    }

    public Optional<QuestDefinition> quest(String id) {
        return Optional.ofNullable(quests.get(id));
    }

    public Optional<PoolDefinition> pool(String id) {
        return Optional.ofNullable(pools.get(id));
    }

    public long errorCount() {
        return issues.stream().filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).count();
    }
}

