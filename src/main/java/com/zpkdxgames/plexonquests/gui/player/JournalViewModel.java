package com.zpkdxgames.plexonquests.gui.player;

/** Immutable per-open summary used by Journal Home. */
public record JournalViewModel(
        int active,
        int readyToClaim,
        int eligible,
        int locked,
        long completed,
        String trackedQuest,
        JournalNextAction nextAction) {

    public JournalViewModel {
        active = Math.max(0, active);
        readyToClaim = Math.max(0, readyToClaim);
        eligible = Math.max(0, eligible);
        locked = Math.max(0, locked);
        completed = Math.max(0L, completed);
        trackedQuest = trackedQuest == null ? "" : trackedQuest;
        nextAction = nextAction == null ? JournalNextAction.BROWSE_ELIGIBLE : nextAction;
    }

    public boolean hasTrackedQuest() {
        return !trackedQuest.isBlank();
    }
}
