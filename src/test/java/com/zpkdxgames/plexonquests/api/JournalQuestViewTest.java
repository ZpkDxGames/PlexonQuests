package com.zpkdxgames.plexonquests.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zpkdxgames.plexonquests.quest.JournalState;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JournalQuestViewTest {
    @Test
    void defensivelyCopiesPrerequisiteSets() {
        Set<String> source = new LinkedHashSet<>(Set.of("first-steps"));
        JournalQuestView view = new JournalQuestView(
                "advanced", "story", "MILESTONE", JournalState.LOCKED,
                0, 10, false, source, source);
        source.add("late-mutation");

        assertEquals(Set.of("first-steps"), view.prerequisites());
        assertEquals(Set.of("first-steps"), view.missingPrerequisites());
    }

    @Test
    void exposedSetsAreUnmodifiable() {
        JournalQuestView view = new JournalQuestView(
                "advanced", "story", "MILESTONE", JournalState.AVAILABLE,
                0, 10, false, Set.of("first-steps"), Set.of());

        assertThrows(UnsupportedOperationException.class, () -> view.prerequisites().add("mutation"));
    }
}
