package com.zpkdxgames.plexonquests.quest;

public enum AssignmentState {
    ACTIVE,
    COMPLETED,
    CLAIMING,
    CLAIMED,
    EXPIRED,
    CANCELLED;

    public boolean terminal() {
        return this == CLAIMED || this == EXPIRED || this == CANCELLED;
    }
}

