package com.zpkdxgames.plexonquests.gui.player;

import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.util.Objects;

/** Immutable origin context carried into details/history so Back restores the exact player view. */
public record JournalNavigationContext(
        JournalView view,
        int page,
        JournalFilter filter,
        String category,
        QuestScope scope) {

    public JournalNavigationContext {
        view = Objects.requireNonNull(view, "view");
        page = Math.max(0, page);
        filter = Objects.requireNonNullElse(filter, JournalFilter.ALL);
        category = normalize(category);
    }

    public static JournalNavigationContext home() {
        return new JournalNavigationContext(JournalView.HOME, 0, JournalFilter.ALL, null, null);
    }

    public static JournalNavigationContext active(int page, QuestScope scope) {
        return new JournalNavigationContext(JournalView.ACTIVE, page, JournalFilter.ALL, null, scope);
    }

    public static JournalNavigationContext eligible(int page, JournalFilter filter, String category, QuestScope scope) {
        return new JournalNavigationContext(JournalView.ELIGIBLE, page, filter, category, scope);
    }

    public static JournalNavigationContext completed(int page) {
        return new JournalNavigationContext(JournalView.COMPLETED, page, JournalFilter.ALL, null, null);
    }

    public JournalNavigationContext withPage(int nextPage) {
        return new JournalNavigationContext(view, nextPage, filter, category, scope);
    }

    public JournalNavigationContext withFilter(JournalFilter nextFilter) {
        return new JournalNavigationContext(view, 0, nextFilter, category, scope);
    }

    public JournalNavigationContext withCategory(String nextCategory) {
        return new JournalNavigationContext(view, 0, filter, nextCategory, scope);
    }

    public JournalNavigationContext withScope(QuestScope nextScope) {
        return new JournalNavigationContext(view, 0, filter, category, nextScope);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
