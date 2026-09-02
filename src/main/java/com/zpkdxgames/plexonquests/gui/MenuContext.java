package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.util.UUID;

public record MenuContext(
        MenuType type,
        QuestScope scope,
        QuestStatusFilter filter,
        int page,
        UUID assignmentId,
        MenuContext parent) {

    public static MenuContext journal(QuestScope scope) {
        return new MenuContext(
                MenuType.JOURNAL,
                scope == null ? QuestScope.DAILY : scope,
                QuestStatusFilter.ALL,
                0,
                null,
                null);
    }
}
