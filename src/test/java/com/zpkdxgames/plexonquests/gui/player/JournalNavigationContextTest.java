package com.zpkdxgames.plexonquests.gui.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zpkdxgames.plexonquests.quest.QuestScope;
import org.junit.jupiter.api.Test;

class JournalNavigationContextTest {
    @Test
    void retainsEligibleCategoryScopeAndPage() {
        JournalNavigationContext context = JournalNavigationContext.eligible(
                2, JournalFilter.LOCKED, "Mining", QuestScope.WEEKLY);
        assertEquals(JournalView.ELIGIBLE, context.view());
        assertEquals(2, context.page());
        assertEquals(JournalFilter.LOCKED, context.filter());
        assertEquals("Mining", context.category());
        assertEquals(QuestScope.WEEKLY, context.scope());
    }

    @Test
    void changingOneDimensionResetsPageButPreservesOtherFilters() {
        JournalNavigationContext context = JournalNavigationContext.eligible(
                2, JournalFilter.LOCKED, "Mining", QuestScope.WEEKLY);
        JournalNavigationContext changed = context.withCategory("Building");
        assertEquals(0, changed.page());
        assertEquals(JournalFilter.LOCKED, changed.filter());
        assertEquals("Building", changed.category());
        assertEquals(QuestScope.WEEKLY, changed.scope());
    }

    @Test
    void pageNavigationPreservesExactOriginFilters() {
        JournalNavigationContext context = JournalNavigationContext.eligible(
                2, JournalFilter.ELIGIBLE, "Mining", QuestScope.DAILY);
        JournalNavigationContext next = context.withPage(3);
        assertEquals(JournalFilter.ELIGIBLE, next.filter());
        assertEquals("Mining", next.category());
        assertEquals(QuestScope.DAILY, next.scope());
        assertEquals(3, next.page());
    }
}
