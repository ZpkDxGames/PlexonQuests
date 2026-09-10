package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestPrerequisiteServiceTest {
    @TempDir Path temp;

    @Test
    void acceptsAcyclicGraphAndNormalizesDuplicatePrerequisites() throws Exception {
        quest("first", true, "");
        quest("second", true, "  completed-quests:\n    - first\n    - FIRST\n");

        var result = QuestPrerequisiteService.validate(temp);

        assertTrue(result.valid());
        assertEquals(2, result.snapshot().questCount());
        assertEquals(java.util.Set.of("first"), result.snapshot().prerequisites().get("second"));
    }

    @Test
    void rejectsMissingPrerequisite() throws Exception {
        quest("second", true, "  completed-quests:\n    - absent\n");

        var result = QuestPrerequisiteService.validate(temp);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("missing prerequisite quest absent")));
    }

    @Test
    void rejectsSelfDependency() throws Exception {
        quest("self", true, "  completed-quests:\n    - self\n");

        var result = QuestPrerequisiteService.validate(temp);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("cannot depend on itself")));
    }

    @Test
    void rejectsDependencyCycle() throws Exception {
        quest("alpha", true, "  completed-quests:\n    - beta\n");
        quest("beta", true, "  completed-quests:\n    - alpha\n");

        var result = QuestPrerequisiteService.validate(temp);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("prerequisite cycle")));
    }

    @Test
    void rejectsThreeNodeCycle() throws Exception {
        quest("alpha", true, "  completed-quests:\n    - beta\n");
        quest("beta", true, "  completed-quests:\n    - gamma\n");
        quest("gamma", true, "  completed-quests:\n    - alpha\n");

        var result = QuestPrerequisiteService.validate(temp);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("prerequisite cycle")));
    }

    @Test
    void rejectsTransitiveUnreachableDisabledChain() throws Exception {
        quest("base", false, "");
        quest("middle", true, "  completed-quests:\n    - base\n");
        quest("top", true, "  completed-quests:\n    - middle\n");

        var result = QuestPrerequisiteService.validate(temp);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("base") && error.contains("unreachable")));
    }

    @Test
    void rejectsEnabledQuestDependingOnDisabledQuest() throws Exception {
        quest("tutorial", false, "");
        quest("advanced", true, "  completed-quests:\n    - tutorial\n");

        var result = QuestPrerequisiteService.validate(temp);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("disabled") && error.contains("unreachable")));
    }

    private void quest(String id, boolean enabled, String prerequisiteBody) throws Exception {
        Path root = temp.resolve("quests");
        Files.createDirectories(root);
        Files.writeString(root.resolve(id + ".yml"), """
                id: %s
                enabled: %s
                prerequisites:
                %s
                """.formatted(id, enabled, prerequisiteBody));
    }
}
