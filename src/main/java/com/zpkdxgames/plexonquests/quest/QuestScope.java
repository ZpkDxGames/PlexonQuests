package com.zpkdxgames.plexonquests.quest;

public enum QuestScope {
    DAILY,
    WEEKLY,
    MILESTONE,
    MANUAL;

    public boolean rotating() {
        return this == DAILY || this == WEEKLY;
    }
}

