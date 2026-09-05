package com.zpkdxgames.plexonquests.rotation;

import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.Predicate;

public final class WeightedSelector {
    public List<QuestDefinition> select(
            PoolDefinition pool,
            Map<String, QuestDefinition> definitions,
            int amount,
            long seed,
            Set<String> excludedQuestIds,
            Predicate<QuestDefinition> eligible) {
        return select(pool, definitions, amount, seed, excludedQuestIds, List.of(), eligible);
    }

    public List<QuestDefinition> select(
            PoolDefinition pool,
            Map<String, QuestDefinition> definitions,
            int amount,
            long seed,
            Set<String> excludedQuestIds,
            Collection<QuestDefinition> existing,
            Predicate<QuestDefinition> eligible) {
        if (amount <= 0) {
            return List.of();
        }
        SplittableRandom random = new SplittableRandom(seed);
        List<QuestDefinition> selected = new ArrayList<>(amount);
        Set<String> used = new HashSet<>();
        Map<String, Integer> categories = new HashMap<>();
        Map<String, Integer> rarities = new HashMap<>();
        for (QuestDefinition quest : existing) {
            used.add(quest.id());
            categories.merge(quest.category(), 1, Integer::sum);
            rarities.merge(quest.rarity(), 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> minimum : pool.minimumPerRarity().entrySet()) {
            while (selected.size() < amount
                    && rarities.getOrDefault(minimum.getKey(), 0) < minimum.getValue()) {
                QuestDefinition chosen = choose(
                        pool, definitions, random, excludedQuestIds, used, categories, rarities,
                        quest -> quest.rarity().equals(minimum.getKey()) && eligible.test(quest));
                if (chosen == null) {
                    break;
                }
                add(chosen, selected, used, categories, rarities);
            }
        }

        for (String category : pool.guaranteedCategories()) {
            if (selected.size() >= amount) {
                break;
            }
            if (categories.getOrDefault(category, 0) > 0) {
                continue;
            }
            QuestDefinition chosen = choose(
                    pool, definitions, random, excludedQuestIds, used, categories, rarities,
                    quest -> quest.category().equals(category) && eligible.test(quest));
            if (chosen != null) {
                add(chosen, selected, used, categories, rarities);
            }
        }

        while (selected.size() < amount) {
            QuestDefinition chosen = choose(
                    pool, definitions, random, excludedQuestIds, used, categories, rarities, eligible);
            if (chosen == null) {
                break;
            }
            add(chosen, selected, used, categories, rarities);
        }
        return List.copyOf(selected);
    }

    private QuestDefinition choose(
            PoolDefinition pool,
            Map<String, QuestDefinition> definitions,
            SplittableRandom random,
            Set<String> excluded,
            Set<String> used,
            Map<String, Integer> categories,
            Map<String, Integer> rarities,
            Predicate<QuestDefinition> eligible) {
        List<Candidate> candidates = new ArrayList<>();
        long total = 0L;
        for (Map.Entry<String, Integer> entry : pool.questWeights().entrySet()) {
            QuestDefinition quest = definitions.get(entry.getKey());
            if (quest == null || !quest.enabled() || excluded.contains(quest.id()) || !eligible.test(quest)) {
                continue;
            }
            if (pool.preventDuplicates() && used.contains(quest.id())) {
                continue;
            }
            if (categories.getOrDefault(quest.category(), 0) >= pool.maximumPerCategory()) {
                continue;
            }
            if (rarities.getOrDefault(quest.rarity(), 0)
                    >= pool.maximumPerRarity().getOrDefault(quest.rarity(), Integer.MAX_VALUE)) {
                continue;
            }
            int weight = Math.max(1, entry.getValue());
            total = Math.addExact(total, weight);
            candidates.add(new Candidate(quest, total));
        }
        if (candidates.isEmpty()) {
            return null;
        }
        long roll = random.nextLong(total);
        for (Candidate candidate : candidates) {
            if (roll < candidate.cumulativeWeight()) {
                return candidate.quest();
            }
        }
        return candidates.getLast().quest();
    }

    private static void add(
            QuestDefinition quest,
            List<QuestDefinition> selected,
            Set<String> used,
            Map<String, Integer> categories,
            Map<String, Integer> rarities) {
        selected.add(quest);
        used.add(quest.id());
        categories.merge(quest.category(), 1, Integer::sum);
        rarities.merge(quest.rarity(), 1, Integer::sum);
    }

    private record Candidate(QuestDefinition quest, long cumulativeWeight) {}
}
