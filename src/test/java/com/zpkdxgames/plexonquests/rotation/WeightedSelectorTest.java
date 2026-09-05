package com.zpkdxgames.plexonquests.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeightedSelectorTest {
    @Test
    void selectionIsDeterministicAndHonorsExclusionsAndCategoryCaps() {
        Map<String, QuestDefinition> quests = new LinkedHashMap<>();
        quests.put("mining-a", TestFixtures.quest(
                "mining-a", "mining", QuestScope.DAILY, CompletionMode.ALL, 1L));
        quests.put("mining-b", TestFixtures.quest(
                "mining-b", "mining", QuestScope.DAILY, CompletionMode.ALL, 1L));
        quests.put("gathering-a", TestFixtures.quest(
                "gathering-a", "gathering", QuestScope.DAILY, CompletionMode.ALL, 1L));
        quests.put("combat-a", TestFixtures.quest(
                "combat-a", "combat", QuestScope.DAILY, CompletionMode.ALL, 1L));
        PoolDefinition pool = pool(Map.of(
                "mining-a", 10,
                "mining-b", 10,
                "gathering-a", 10,
                "combat-a", 10));
        WeightedSelector selector = new WeightedSelector();

        List<QuestDefinition> first = selector.select(
                pool, quests, 3, 42L, Set.of("mining-a"), ignored -> true);
        List<QuestDefinition> second = selector.select(
                pool, quests, 3, 42L, Set.of("mining-a"), ignored -> true);

        assertEquals(first, second);
        assertEquals(3, first.size());
        assertEquals("gathering", first.getFirst().category());
        assertFalse(first.stream().anyMatch(quest -> quest.id().equals("mining-a")));
        assertEquals(1L, first.stream().filter(quest -> quest.category().equals("mining")).count());
    }

    @Test
    void rarityFloorAndCapAreAppliedBeforeNormalSelection() {
        QuestDefinition commonA = TestFixtures.quest(
                "common-a", "general", QuestScope.DAILY, CompletionMode.ALL, 1L);
        QuestDefinition commonB = TestFixtures.quest(
                "common-b", "general", QuestScope.DAILY, CompletionMode.ALL, 1L);
        QuestDefinition rare = withRarity(TestFixtures.quest(
                "rare-a", "special", QuestScope.DAILY, CompletionMode.ALL, 1L), "RARE");
        Map<String, QuestDefinition> quests = Map.of(
                commonA.id(), commonA,
                commonB.id(), commonB,
                rare.id(), rare);
        PoolDefinition pool = pool(
                Map.of(commonA.id(), 10, commonB.id(), 10, rare.id(), 1),
                List.of(),
                3,
                Map.of("RARE", 1),
                Map.of("COMMON", 1));

        List<QuestDefinition> selected = new WeightedSelector().select(
                pool, quests, 2, 9L, Set.of(), ignored -> true);

        assertEquals(2, selected.size());
        assertEquals("RARE", selected.getFirst().rarity());
        assertEquals(1L, selected.stream().filter(quest -> quest.rarity().equals("COMMON")).count());
    }

    @Test
    void guaranteedCategoriesNeverPushSelectionPastRequestedAmount() {
        QuestDefinition rare = withRarity(TestFixtures.quest(
                "rare-a", "special", QuestScope.DAILY, CompletionMode.ALL, 1L), "RARE");
        QuestDefinition gathering = TestFixtures.quest(
                "gathering-a", "gathering", QuestScope.DAILY, CompletionMode.ALL, 1L);
        PoolDefinition pool = pool(
                Map.of(rare.id(), 10, gathering.id(), 10),
                List.of("gathering"),
                2,
                Map.of("RARE", 1),
                Map.of());

        List<QuestDefinition> selected = new WeightedSelector().select(
                pool, Map.of(rare.id(), rare, gathering.id(), gathering), 1, 4L, Set.of(), ignored -> true);

        assertEquals(1, selected.size());
        assertEquals("rare-a", selected.getFirst().id());
    }

    @Test
    void existingAssignmentsSeedDuplicateCategoryAndRarityConstraints() {
        QuestDefinition existing = withRarity(TestFixtures.quest(
                "mining-a", "mining", QuestScope.DAILY, CompletionMode.ALL, 1L), "RARE");
        QuestDefinition mining = TestFixtures.quest(
                "mining-b", "mining", QuestScope.DAILY, CompletionMode.ALL, 1L);
        QuestDefinition gathering = TestFixtures.quest(
                "gathering-a", "gathering", QuestScope.DAILY, CompletionMode.ALL, 1L);
        PoolDefinition pool = pool(
                Map.of(existing.id(), 10, mining.id(), 100, gathering.id(), 1),
                List.of("mining"),
                1,
                Map.of("RARE", 1),
                Map.of("RARE", 1));

        List<QuestDefinition> selected = new WeightedSelector().select(
                pool,
                Map.of(existing.id(), existing, mining.id(), mining, gathering.id(), gathering),
                1,
                11L,
                Set.of(),
                List.of(existing),
                ignored -> true);

        assertEquals(List.of("gathering-a"), selected.stream().map(QuestDefinition::id).toList());
    }

    private static PoolDefinition pool(Map<String, Integer> weights) {
        return pool(weights, List.of("gathering"), 1, Map.of(), Map.of());
    }

    private static PoolDefinition pool(
            Map<String, Integer> weights,
            List<String> guaranteedCategories,
            int maximumPerCategory,
            Map<String, Integer> minimumPerRarity,
            Map<String, Integer> maximumPerRarity) {
        return new PoolDefinition(
                "daily",
                true,
                QuestScope.DAILY,
                3,
                true,
                Duration.ofDays(7),
                guaranteedCategories,
                maximumPerCategory,
                minimumPerRarity,
                maximumPerRarity,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                weights,
                "test");
    }

    private static QuestDefinition withRarity(QuestDefinition quest, String rarity) {
        return new QuestDefinition(
                quest.id(),
                quest.revision(),
                quest.enabled(),
                quest.scope(),
                quest.category(),
                rarity,
                quest.weight(),
                quest.eligibility(),
                quest.display(),
                quest.completionMode(),
                quest.claimMode(),
                quest.objectives(),
                quest.rewards(),
                quest.completeEffect(),
                quest.claimEffect(),
                quest.fingerprint(),
                quest.source());
    }
}
