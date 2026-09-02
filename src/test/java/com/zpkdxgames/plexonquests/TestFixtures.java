package com.zpkdxgames.plexonquests;

import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.objective.ObjectiveFilters;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.quest.ClaimMode;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.IconDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDisplay;
import com.zpkdxgames.plexonquests.quest.QuestEligibility;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.reward.RewardBundle;
import com.zpkdxgames.plexonquests.reward.RewardDefinition;
import com.zpkdxgames.plexonquests.reward.RewardMode;
import com.zpkdxgames.plexonquests.reward.RewardType;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

public final class TestFixtures {
    private TestFixtures() {}

    public static QuestDefinition quest(String id, CompletionMode mode, long... objectiveAmounts) {
        return quest(id, "general", QuestScope.DAILY, mode, objectiveAmounts);
    }

    public static QuestDefinition quest(
            String id,
            String category,
            QuestScope scope,
            CompletionMode mode,
            long... objectiveAmounts) {
        Map<String, ObjectiveDefinition> objectives = new LinkedHashMap<>();
        for (int index = 0; index < objectiveAmounts.length; index++) {
            String objectiveId = "objective_" + (index + 1);
            objectives.put(objectiveId, new ObjectiveDefinition(
                    objectiveId,
                    ObjectiveType.BREAK_BLOCK,
                    objectiveAmounts[index],
                    "<gray>" + objectiveId,
                    ObjectiveFilters.empty()));
        }
        RewardDefinition reward = new RewardDefinition(
                "experience",
                RewardType.EXPERIENCE_POINTS,
                25L,
                0D,
                null,
                "",
                "",
                Duration.ZERO,
                "",
                "",
                "",
                "<aqua>25 experience",
                1);
        return new QuestDefinition(
                id,
                1,
                true,
                scope,
                category,
                "COMMON",
                10,
                new QuestEligibility("", Set.of(), Set.of(), Set.of(), Set.of()),
                new QuestDisplay(IconDefinition.of(Material.PAPER), "<white>" + id, "A test quest", "default"),
                mode,
                ClaimMode.MANUAL,
                objectives,
                new RewardBundle(RewardMode.ALL, List.of(reward)),
                "quest-complete",
                "quest-claim",
                "fingerprint-" + id,
                "test");
    }
}
