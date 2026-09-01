package com.zpkdxgames.plexonquests.gui;

public enum QuestStatusFilter {
    ACTIVE,
    COMPLETED,
    ALL;

    public QuestStatusFilter next() {
        QuestStatusFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

